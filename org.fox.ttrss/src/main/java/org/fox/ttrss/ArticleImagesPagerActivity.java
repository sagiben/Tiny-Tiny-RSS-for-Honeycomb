package org.fox.ttrss;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.util.Log;
import android.view.ContextMenu;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.assist.FailReason;
import com.nostra13.universalimageloader.core.display.FadeInBitmapDisplayer;
import com.nostra13.universalimageloader.core.listener.ImageLoadingListener;
import com.viewpagerindicator.UnderlinePageIndicator;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;

import it.sephiroth.android.library.imagezoom.ImageViewTouch;

public class ArticleImagesPagerActivity extends CommonActivity implements GestureDetector.OnDoubleTapListener {
    private final String TAG = this.getClass().getSimpleName();

    private ArrayList<String> m_urls;
    private ArrayList<String> m_checkedUrls;
    private String m_title;
    private ArticleImagesPagerAdapter m_adapter;
    private String m_content;
    private GestureDetector m_detector;

    @Override
    public boolean onSingleTapConfirmed(MotionEvent motionEvent) {
        ActionBar bar = getSupportActionBar();

        if (bar.isShowing()) {
            bar.hide();
        } else {
            bar.show();
        }

        return false;
    }

    @Override
    public boolean onDoubleTap(MotionEvent motionEvent) {
        return false;
    }

    @Override
    public boolean onDoubleTapEvent(MotionEvent motionEvent) {
        return false;
    }

    private class ArticleImagesPagerAdapter extends PagerAdapter {
        private List<String> m_urls;

        public ArticleImagesPagerAdapter(List<String> urls) {
            super();

            m_urls = urls;
        }

        public ArticleImagesPagerAdapter() {
            super();
        }

        @Override
        public int getCount() {
            return m_urls.size();
        }

        @Override
        public boolean isViewFromObject(View view, Object o) {
            return view == o;
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            String url = m_urls.get(position);

            Log.d(TAG, "called for URL: " + url);

            LayoutInflater inflater = (LayoutInflater) container.getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

            View view = inflater.inflate(R.layout.article_images_image, null);

            m_detector = new GestureDetector(ArticleImagesPagerActivity.this, new GestureDetector.SimpleOnGestureListener());

            m_detector.setOnDoubleTapListener(ArticleImagesPagerActivity.this);

            ImageViewTouch imgView = (ImageViewTouch) view.findViewById(R.id.flavor_image);

            imgView.setFitToScreen(true);
            //imgView.setFitToWidth(true);

            imgView.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View view, MotionEvent event) {
                    return m_detector.onTouchEvent(event);
                }
            });

            registerForContextMenu(imgView);
            getSupportActionBar().hide();

            DisplayImageOptions options = new DisplayImageOptions.Builder()
                    .cacheInMemory(true)
                    .resetViewBeforeLoading(true)
                    .cacheOnDisk(true)
                    .displayer(new FadeInBitmapDisplayer(200))
                    .build();

            final ProgressBar progressBar = (ProgressBar) view.findViewById(R.id.flavor_image_progress);
            final View errorMessage = view.findViewById(R.id.flavor_image_error);

            ImageLoader.getInstance().displayImage(url, imgView, options, new ImageLoadingListener() {
                @Override
                public void onLoadingStarted(String s, View view) {

                }

                @Override
                public void onLoadingFailed(String s, View view, FailReason failReason) {
                    progressBar.setVisibility(View.GONE);
                    errorMessage.setVisibility(View.VISIBLE);
                }

                @Override
                public void onLoadingComplete(String s, View view, Bitmap bitmap) {
                    if (bitmap != null) {
                        view.setTag(s);
                    }

                    progressBar.setVisibility(View.GONE);
                }

                @Override
                public void onLoadingCancelled(String s, View view) {

                }
            });

            ((ViewPager) container).addView(view, 0);

            return view;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            container.removeView((FrameLayout)object);
        }
    }

    private class ImageCheckTask extends AsyncTask<List<String>, String, Integer> {
        @Override
        protected Integer doInBackground(List<String>... urls) {
            int position = 0;

            for (String url : urls[0]) {
                if (!isCancelled()) {
                    position++;

                    //Log.d(TAG, "checking: " + url);

                    DisplayImageOptions options = new DisplayImageOptions.Builder()
                            .cacheInMemory(true)
                            .cacheOnDisk(true)
                            .build();

                    Bitmap bmp = ImageLoader.getInstance().loadImageSync(url, options);

                    int progress = (int) ((position / (float)urls[0].size()) * 10000);

                    if (bmp != null && bmp.getWidth() > 128 && bmp.getHeight() > 128) {
                        publishProgress(url, String.valueOf(progress));
                    } else {
                        publishProgress(null, String.valueOf(progress));
                    }
                }
            }

            return -1;
        }

        @Override
        protected void onProgressUpdate(String... checkedUrl) {

            if (!isFinishing() && m_adapter != null) {
                if (checkedUrl[0] != null) {
                    m_checkedUrls.add(checkedUrl[0]);
                    m_adapter.notifyDataSetChanged();
                }

                setProgress(Integer.valueOf(checkedUrl[1]));
            } else {
                cancel(true);
            }
        }

        @Override
        protected void onPostExecute(Integer result) {
            //
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // we use that before parent onCreate so let's init locally
        m_prefs = PreferenceManager
                .getDefaultSharedPreferences(getApplicationContext());
        setAppTheme(m_prefs);

        super.onCreate(savedInstanceState);

        setContentView(R.layout.article_images_pager);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        if (savedInstanceState == null) {
            m_title = getIntent().getStringExtra("title");
            //m_urls = getIntent().getStringArrayListExtra("urls");
            m_content = getIntent().getStringExtra("content");

            String imgSrcFirst = getIntent().getStringExtra("firstSrc");

            m_urls = new ArrayList<String>();

            Document doc = Jsoup.parse(m_content);
            Elements imgs = doc.select("img");

            boolean firstFound = false;

            for (Element img : imgs) {
                String imgSrc = img.attr("src");

                if (imgSrcFirst.equals(imgSrc))
                    firstFound = true;

                if (firstFound) {
                    if (imgSrc.indexOf("//") == 0)
                        imgSrc = "http:" + imgSrc;

                    m_urls.add(imgSrc);
                }
            }

        } else {
            m_urls = savedInstanceState.getStringArrayList("urls");
            m_title = savedInstanceState.getString("title");
            m_content = savedInstanceState.getString("content");
        }

        if (m_urls.size() > 1) {
            m_checkedUrls = new ArrayList<String>();

            ArrayList<String> tmp = new ArrayList<String>(m_urls);

            m_checkedUrls.add(tmp.get(0));
            tmp.remove(0);

            ImageCheckTask ict = new ImageCheckTask();
            ict.execute(tmp);
        } else {
            m_checkedUrls = new ArrayList<String>(m_urls);
        }

        setTitle(m_title);

        m_adapter = new ArticleImagesPagerAdapter(m_checkedUrls);

        ViewPager pager = (ViewPager) findViewById(R.id.article_images_pager);
        pager.setAdapter(m_adapter);

        UnderlinePageIndicator indicator = (UnderlinePageIndicator)findViewById(R.id.article_images_indicator);
        indicator.setViewPager(pager);
    }

    @SuppressLint("NewApi")
    @Override
    public void onResume() {
        super.onResume();

        if (m_prefs.getBoolean("full_screen_mode", false)) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
            getSupportActionBar().hide();
        }
    }


    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
                                    ContextMenu.ContextMenuInfo menuInfo) {

        getMenuInflater().inflate(R.menu.article_content_img_context_menu, menu);

        super.onCreateContextMenu(menu, v, menuInfo);
    }


    @Override
    public void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);

        out.putStringArrayList("urls", m_urls);
        out.putString("title", m_title);
        out.putString("content", m_content);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.article_content_img_context_menu, menu);


        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return onContextItemSelected(item); // this is really bad :()
    }

    @Override
    public boolean onContextItemSelected(android.view.MenuItem item) {
        ViewPager pager = (ViewPager) findViewById(R.id.article_images_pager);
        String url = null;

        if (pager != null) {
            int currentItem = pager.getCurrentItem();
            url = m_urls.get(currentItem);
        }

        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            case R.id.article_img_open:
                if (url != null) {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW,
                                Uri.parse(url));
                        startActivity(intent);
                    } catch (Exception e) {
                        e.printStackTrace();
                        toast(R.string.error_other_error);
                    }
                }
                return true;
            case R.id.article_img_copy:
                if (url != null) {
                    copyToClipboard(url);
                }
                return true;
            case R.id.article_img_share:
                if (url != null) {
                    Intent intent = new Intent(Intent.ACTION_SEND);

                    intent.setType("image/png");
                    intent.putExtra(Intent.EXTRA_SUBJECT, url);
                    intent.putExtra(Intent.EXTRA_TEXT, url);

                    startActivity(Intent.createChooser(intent, url));
                }
                return true;
            // TODO: this needs access to article text, I'm afraid
            case R.id.article_img_view_caption:
                if (url != null) {

                    // Android doesn't give us an easy way to access title tags;
                    // we'll use Jsoup on the body text to grab the title text
                    // from the first image tag with this url. This will show
                    // the wrong text if an image is used multiple times.
                    Document doc = Jsoup.parse(m_content);
                    Elements es = doc.getElementsByAttributeValue("src", url);
                    if (es.size() > 0) {
                        if (es.get(0).hasAttr("title")) {
                            Dialog dia = new Dialog(this);
                            if (es.get(0).hasAttr("alt")) {
                                dia.setTitle(es.get(0).attr("alt"));
                            } else {
                                dia.setTitle(es.get(0).attr("title"));
                            }
                            TextView titleText = new TextView(this);

                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
                                titleText.setPaddingRelative(24, 24, 24, 24);
                            } else {
                                titleText.setPadding(24, 24, 24, 24);
                            }

                            titleText.setTextSize(16);
                            titleText.setText(es.get(0).attr("title"));
                            dia.setContentView(titleText);
                            dia.show();
                        } else {
                            toast(R.string.no_caption_to_display);
                        }
                    } else {
                        toast(R.string.no_caption_to_display);
                    }
                }
                return true;
            default:
                Log.d(TAG, "onContextItemSelected, unhandled id=" + item.getItemId());
                return super.onContextItemSelected(item);
        }
    }
}
