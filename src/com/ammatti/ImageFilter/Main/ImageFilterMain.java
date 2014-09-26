package com.ammatti.ImageFilter.Main;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import com.ammatti.ImageFilter.*;
import com.ammatti.ImageFilter.Distort.*;
import com.ammatti.ImageFilter.Textures.*;
import com.ammatti.ImageFilter.R;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Gallery;
import android.widget.ImageView;
import android.widget.TextView;

public class ImageFilterMain extends Activity {

	private ImageView imageView;
	private TextView runningAlertView;
	private TextView filterNameView;
	private String logTag = "ImageFilterMain";
	
	//user chosen file
	private final int SELECT_PHOTO = 1;
	protected Uri selectedImageUri;
	//user save file
	private File dirPath = new File(Environment.getExternalStorageDirectory() + "/DCIM/Camera");
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		
		imageView= (ImageView) findViewById(R.id.imgfilter);
		runningAlertView = (TextView) findViewById(R.id.runtime);
		filterNameView = (TextView) findViewById(R.id.filtername);
		//set default image path
		selectedImageUri = Uri.parse("android.resource://" + this.getPackageName() + "/"+ R.drawable.finland);
		//control image size smaller than 480*480 to avoid memory leak on Gaussian operation
		//show original image 
		Bitmap bitmap = BitmapFactory.decodeResource(ImageFilterMain.this.getResources(), R.drawable.finland);
		imageView.setImageBitmap(bitmap);

		LoadImageFilter();
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			Log.i(logTag,"Press action_settings");
			return true;
		}else if(id == R.id.action_search){
			Log.i(logTag,"Press action_search");
			Intent photoPickerIntent = new Intent(Intent.ACTION_PICK);
			photoPickerIntent.setType("image/*");
			startActivityForResult(photoPickerIntent, SELECT_PHOTO);
			return true;
		}else if(id == R.id.action_save){
			Log.i(logTag,"Press action_save");
			long start = System.currentTimeMillis();
			//
			imageView.setDrawingCacheEnabled(true);
			Bitmap orgBitmap = imageView.getDrawingCache();
			if(orgBitmap == null){
	            Log.i(logTag,"orgBitmap == null");
				return true;
	        }
			String filename = "imagefilter"+start+".jpg";
			Log.i(logTag,filename);
			File save = new File(dirPath, filename);
			FileOutputStream strm;
			try {
				strm = new FileOutputStream(save);
				orgBitmap.compress(CompressFormat.JPEG, 100, strm);
				Log.i(logTag,"orgBitmap.compress");
				strm.flush();
			    strm.close();
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			imageView.setDrawingCacheEnabled(false);
			//make MediaScannerReceiver refresh and display the new file immediately
			Uri savedFilePath = Uri.parse("file:///"+save.getAbsolutePath());
			sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, savedFilePath));  
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
	
	@Override
    protected void onActivityResult(int requestCode, int resultCode, Intent imageReturnedIntent) { 
        super.onActivityResult(requestCode, resultCode, imageReturnedIntent); 

        switch(requestCode) { 
        case SELECT_PHOTO:
            if(resultCode == RESULT_OK){
				try {
					selectedImageUri = imageReturnedIntent.getData();
					InputStream imageStream = getContentResolver().openInputStream(selectedImageUri);
					BitmapFactory.Options options = new BitmapFactory.Options();
				    options.inPurgeable = true;
				    options.inInputShareable = true;
				    //options.inSampleSize = 4;
					Bitmap selectedImage = BitmapFactory.decodeStream(imageStream,null, options);
					imageView.setImageBitmap(selectedImage);
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				}

            }
        }
    }
	
	/**
	 * Load filter
	 */
	private void LoadImageFilter() {
		Gallery gallery = (Gallery) findViewById(R.id.galleryFilter);
		final ImageFilterAdapter filterAdapter = new ImageFilterAdapter(
				ImageFilterMain.this);
		gallery.setAdapter(new ImageFilterAdapter(ImageFilterMain.this));
		gallery.setSelection(2);
		gallery.setAnimationDuration(3000);
		gallery.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			public void onItemClick(AdapterView<?> arg0, View arg1, int position, long id) {
				IImageFilter filter = (IImageFilter) filterAdapter.getItem(position);
				new processImageTask(ImageFilterMain.this, filter).execute();
			}
		});
	}

	public class processImageTask extends AsyncTask<Void, Void, Bitmap> {
		private IImageFilter filter;
        private Activity activity = null;
		public processImageTask(Activity activity, IImageFilter imageFilter) {
			this.filter = imageFilter;
			this.activity = activity;
		}

		@Override
		protected void onPreExecute() {
			// TODO Auto-generated method stub
			super.onPreExecute();
			runningAlertView.setVisibility(View.VISIBLE);
			filterNameView.setText(filter.getClass().getSimpleName());
		}

		public Bitmap doInBackground(Void... params) {
			Image img = null;
			try
	    	{   
				//get source image
				Uri temp = selectedImageUri;
				InputStream imageStream = getContentResolver().openInputStream(temp);
				Bitmap bitmap = BitmapFactory.decodeStream(imageStream);
				img = new Image(bitmap);
				if (filter != null) {
					img = filter.process(img);
					img.copyPixelsFromBuffer();
				}
				return img.getImage();
	    	}
			catch(Exception e){
				if (img != null && img.destImage.isRecycled()) {
					img.destImage.recycle();
					img.destImage = null;
					System.gc(); // notify system recycle resource
				}
			}
			finally{
				if (img != null && img.image.isRecycled()) {
					img.image.recycle();
					img.image = null;
					System.gc(); // notify system recycle resource
				}
			}
			return null;
		}
		
		@Override
		protected void onPostExecute(Bitmap result) {
			if(result != null){
				super.onPostExecute(result);
				imageView.setImageBitmap(result);	
			}
			runningAlertView.setVisibility(View.GONE);
		}
	}

	public class ImageFilterAdapter extends BaseAdapter {
		private class FilterInfo {
			public int filterID;
			public IImageFilter filter;

			public FilterInfo(int filterID, IImageFilter filter) {
				this.filterID = filterID;
				this.filter = filter;
			}
		}

		private Context mContext;
		private List<FilterInfo> filterArray = new ArrayList<FilterInfo>();

		public ImageFilterAdapter(Context c) {
			mContext = c;
			
			//99 kinds for filtering condition
	         
	        //v0.4 
			filterArray.add(new FilterInfo(R.drawable.video_filter1, new VideoFilter(VideoFilter.VIDEO_TYPE.VIDEO_STAGGERED)));
			filterArray.add(new FilterInfo(R.drawable.video_filter2, new VideoFilter(VideoFilter.VIDEO_TYPE.VIDEO_TRIPED)));
			filterArray.add(new FilterInfo(R.drawable.video_filter3, new VideoFilter(VideoFilter.VIDEO_TYPE.VIDEO_3X3)));
			filterArray.add(new FilterInfo(R.drawable.video_filter4, new VideoFilter(VideoFilter.VIDEO_TYPE.VIDEO_DOTS)));
			filterArray.add(new FilterInfo(R.drawable.tilereflection_filter1, new TileReflectionFilter(20, 8, 45, (byte)1)));
			filterArray.add(new FilterInfo(R.drawable.tilereflection_filter2, new TileReflectionFilter(20, 8, 45, (byte)2)));
			filterArray.add(new FilterInfo(R.drawable.fillpattern_filter, new FillPatternFilter(ImageFilterMain.this, R.drawable.texture1)));
			filterArray.add(new FilterInfo(R.drawable.fillpattern_filter1, new FillPatternFilter(ImageFilterMain.this, R.drawable.texture2)));
			filterArray.add(new FilterInfo(R.drawable.mirror_filter1, new MirrorFilter(true)));
			filterArray.add(new FilterInfo(R.drawable.mirror_filter2, new MirrorFilter(false)));
			filterArray.add(new FilterInfo(R.drawable.ycb_crlinear_filter, new YCBCrLinearFilter(new YCBCrLinearFilter.Range(-0.3f, 0.3f))));
			filterArray.add(new FilterInfo(R.drawable.ycb_crlinear_filter2, new YCBCrLinearFilter(new YCBCrLinearFilter.Range(-0.276f, 0.163f), new YCBCrLinearFilter.Range(-0.202f, 0.5f))));
			filterArray.add(new FilterInfo(R.drawable.texturer_filter, new TexturerFilter(new CloudsTexture(), 0.8f, 0.8f)));
			filterArray.add(new FilterInfo(R.drawable.texturer_filter1, new TexturerFilter(new LabyrinthTexture(), 0.8f, 0.8f)));
			filterArray.add(new FilterInfo(R.drawable.texturer_filter2, new TexturerFilter(new MarbleTexture(), 1.8f, 0.8f)));
			filterArray.add(new FilterInfo(R.drawable.texturer_filter3, new TexturerFilter(new WoodTexture(), 0.8f, 0.8f)));
			filterArray.add(new FilterInfo(R.drawable.texturer_filter4, new TexturerFilter(new TextileTexture(), 0.8f, 0.8f)));
			filterArray.add(new FilterInfo(R.drawable.hslmodify_filter, new HslModifyFilter(20f)));
			filterArray.add(new FilterInfo(R.drawable.hslmodify_filter0, new HslModifyFilter(40f)));
			filterArray.add(new FilterInfo(R.drawable.hslmodify_filter1, new HslModifyFilter(60f)));
			filterArray.add(new FilterInfo(R.drawable.hslmodify_filter2, new HslModifyFilter(80f)));
			filterArray.add(new FilterInfo(R.drawable.hslmodify_filter3, new HslModifyFilter(100f)));
			filterArray.add(new FilterInfo(R.drawable.hslmodify_filter4, new HslModifyFilter(150f)));
			filterArray.add(new FilterInfo(R.drawable.hslmodify_filter5, new HslModifyFilter(200f)));
			filterArray.add(new FilterInfo(R.drawable.hslmodify_filter6, new HslModifyFilter(250f)));
			filterArray.add(new FilterInfo(R.drawable.hslmodify_filter7, new HslModifyFilter(300f)));
			
			//v0.3  
			filterArray.add(new FilterInfo(R.drawable.zoomblur_filter, new ZoomBlurFilter(30)));
			filterArray.add(new FilterInfo(R.drawable.threedgrid_filter, new ThreeDGridFilter(16, 100)));
			filterArray.add(new FilterInfo(R.drawable.colortone_filter, new ColorToneFilter(Color.rgb(33, 168, 254), 192)));
			filterArray.add(new FilterInfo(R.drawable.colortone_filter2, new ColorToneFilter(0x00FF00, 192)));//green
			filterArray.add(new FilterInfo(R.drawable.colortone_filter3, new ColorToneFilter(0xFF0000, 192)));//blue
			filterArray.add(new FilterInfo(R.drawable.colortone_filter4, new ColorToneFilter(0x00FFFF, 192)));//yellow
			filterArray.add(new FilterInfo(R.drawable.softglow_filter, new SoftGlowFilter(10, 0.1f, 0.1f)));
			filterArray.add(new FilterInfo(R.drawable.tilereflection_filter, new TileReflectionFilter(20, 8)));
			filterArray.add(new FilterInfo(R.drawable.blind_filter1, new BlindFilter(true, 96, 100, 0xffffff)));
			filterArray.add(new FilterInfo(R.drawable.blind_filter2, new BlindFilter(false, 96, 100, 0x000000)));
			filterArray.add(new FilterInfo(R.drawable.raiseframe_filter, new RaiseFrameFilter(20)));
			filterArray.add(new FilterInfo(R.drawable.shift_filter, new ShiftFilter(10)));
			filterArray.add(new FilterInfo(R.drawable.wave_filter, new WaveFilter(25, 10)));
			filterArray.add(new FilterInfo(R.drawable.bulge_filter, new BulgeFilter(-97)));
			filterArray.add(new FilterInfo(R.drawable.twist_filter, new TwistFilter(27, 106)));
			filterArray.add(new FilterInfo(R.drawable.ripple_filter, new RippleFilter(38, 15, true)));
			filterArray.add(new FilterInfo(R.drawable.illusion_filter, new IllusionFilter(3)));
			filterArray.add(new FilterInfo(R.drawable.supernova_filter, new SupernovaFilter(0x00FFFF,20,100)));
			filterArray.add(new FilterInfo(R.drawable.lensflare_filter, new LensFlareFilter()));
			filterArray.add(new FilterInfo(R.drawable.posterize_filter, new PosterizeFilter(2)));
			filterArray.add(new FilterInfo(R.drawable.gamma_filter, new GammaFilter(50)));
			filterArray.add(new FilterInfo(R.drawable.sharp_filter, new SharpFilter()));
			
			//v0.2
			filterArray.add(new FilterInfo(R.drawable.invert_filter, new ComicFilter()));
			filterArray.add(new FilterInfo(R.drawable.invert_filter, new SceneFilter(5f, Gradient.Scene())));//green
			filterArray.add(new FilterInfo(R.drawable.invert_filter, new SceneFilter(5f, Gradient.Scene1())));//purple
			filterArray.add(new FilterInfo(R.drawable.invert_filter, new SceneFilter(5f, Gradient.Scene2())));//blue
			filterArray.add(new FilterInfo(R.drawable.invert_filter, new SceneFilter(5f, Gradient.Scene3())));
			filterArray.add(new FilterInfo(R.drawable.invert_filter, new FilmFilter(80f)));
			filterArray.add(new FilterInfo(R.drawable.invert_filter, new FocusFilter()));
			filterArray.add(new FilterInfo(R.drawable.invert_filter, new CleanGlassFilter()));
			filterArray.add(new FilterInfo(R.drawable.invert_filter, new PaintBorderFilter(0x00FF00)));//green
			filterArray.add(new FilterInfo(R.drawable.invert_filter, new PaintBorderFilter(0x00FFFF)));//yellow
			filterArray.add(new FilterInfo(R.drawable.invert_filter, new PaintBorderFilter(0xFF0000)));//blue
			filterArray.add(new FilterInfo(R.drawable.invert_filter, new LomoFilter()));
			
			//v0.1
			filterArray.add(new FilterInfo(R.drawable.invert_filter, new InvertFilter()));
			filterArray.add(new FilterInfo(R.drawable.blackwhite_filter, new BlackWhiteFilter()));
			filterArray.add(new FilterInfo(R.drawable.edge_filter, new EdgeFilter()));
			filterArray.add(new FilterInfo(R.drawable.pixelate_filter, new PixelateFilter()));
			filterArray.add(new FilterInfo(R.drawable.neon_filter, new NeonFilter()));
			filterArray.add(new FilterInfo(R.drawable.bigbrother_filter, new BigBrotherFilter()));
			filterArray.add(new FilterInfo(R.drawable.monitor_filter, new MonitorFilter()));
			filterArray.add(new FilterInfo(R.drawable.relief_filter, new ReliefFilter()));
			filterArray.add(new FilterInfo(R.drawable.brightcontrast_filter,new BrightContrastFilter()));
			filterArray.add(new FilterInfo(R.drawable.saturationmodity_filter,	new SaturationModifyFilter()));
			filterArray.add(new FilterInfo(R.drawable.threshold_filter,	new ThresholdFilter()));
			filterArray.add(new FilterInfo(R.drawable.noisefilter,	new NoiseFilter()));
			filterArray.add(new FilterInfo(R.drawable.banner_filter1, new BannerFilter(10, true)));
			filterArray.add(new FilterInfo(R.drawable.banner_filter2, new BannerFilter(10, false)));
			filterArray.add(new FilterInfo(R.drawable.rectmatrix_filter, new RectMatrixFilter()));
			filterArray.add(new FilterInfo(R.drawable.blockprint_filter, new BlockPrintFilter()));
			filterArray.add(new FilterInfo(R.drawable.brick_filter,	new BrickFilter()));
			filterArray.add(new FilterInfo(R.drawable.gaussianblur_filter,	new GaussianBlurFilter()));
			filterArray.add(new FilterInfo(R.drawable.light_filter,	new LightFilter()));
			filterArray.add(new FilterInfo(R.drawable.mosaic_filter,new MistFilter()));
			filterArray.add(new FilterInfo(R.drawable.mosaic_filter,new MosaicFilter()));
			filterArray.add(new FilterInfo(R.drawable.oilpaint_filter,	new OilPaintFilter()));
			filterArray.add(new FilterInfo(R.drawable.radialdistortion_filter,new RadialDistortionFilter()));
			filterArray.add(new FilterInfo(R.drawable.reflection1_filter,new ReflectionFilter(true)));
			filterArray.add(new FilterInfo(R.drawable.reflection2_filter,new ReflectionFilter(false)));
			filterArray.add(new FilterInfo(R.drawable.saturationmodify_filter,	new SaturationModifyFilter()));
			filterArray.add(new FilterInfo(R.drawable.smashcolor_filter,new SmashColorFilter()));
			filterArray.add(new FilterInfo(R.drawable.tint_filter,	new TintFilter()));
			filterArray.add(new FilterInfo(R.drawable.vignette_filter,	new VignetteFilter()));
			filterArray.add(new FilterInfo(R.drawable.autoadjust_filter,new AutoAdjustFilter()));
			filterArray.add(new FilterInfo(R.drawable.colorquantize_filter,	new ColorQuantizeFilter()));
			filterArray.add(new FilterInfo(R.drawable.waterwave_filter,	new WaterWaveFilter()));
			filterArray.add(new FilterInfo(R.drawable.vintage_filter,new VintageFilter()));
			filterArray.add(new FilterInfo(R.drawable.oldphoto_filter,new OldPhotoFilter()));
			filterArray.add(new FilterInfo(R.drawable.sepia_filter,	new SepiaFilter()));
			filterArray.add(new FilterInfo(R.drawable.rainbow_filter,new RainBowFilter()));
			filterArray.add(new FilterInfo(R.drawable.feather_filter,new FeatherFilter()));
			filterArray.add(new FilterInfo(R.drawable.xradiation_filter,new XRadiationFilter()));
			filterArray.add(new FilterInfo(R.drawable.nightvision_filter,new NightVisionFilter()));

			filterArray.add(new FilterInfo(R.drawable.saturationmodity_filter,null)); /*Null is original version */
		}

		public int getCount() {
			return filterArray.size();
		}

		public Object getItem(int position) {
			return position < filterArray.size() ? filterArray.get(position).filter
					: null;
		}

		public long getItemId(int position) {
			return position;
		}

		public View getView(int position, View convertView, ViewGroup parent) {
			Bitmap bmImg = BitmapFactory
					.decodeResource(mContext.getResources(),
							filterArray.get(position).filterID);
			int width = 100;// bmImg.getWidth();
			int height = 100;// bmImg.getHeight();
			bmImg.recycle();
			ImageView imageview = new ImageView(mContext);
			imageview.setImageResource(filterArray.get(position).filterID);
			imageview.setLayoutParams(new Gallery.LayoutParams(width, height));
			imageview.setScaleType(ImageView.ScaleType.FIT_CENTER);// set the scale style
			return imageview;
		}
	};

}
