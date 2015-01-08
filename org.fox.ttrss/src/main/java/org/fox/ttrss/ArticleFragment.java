package org.fox.ttrss;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.text.BidiFormatter;
import android.text.Html;
import android.util.Log;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.webkit.WebBackForwardList;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebView.HitTestResult;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.shamanland.fab.ShowHideOnScroll;

import org.fox.ttrss.types.Article;
import org.fox.ttrss.types.Attachment;
import org.fox.ttrss.util.TypefaceCache;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.text.Bidi;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ArticleFragment extends Fragment  {
	private final String TAG = this.getClass().getSimpleName();

	private SharedPreferences m_prefs;
	private Article m_article;
	private HeadlinesActivity m_activity;
    private WebView m_web;
    protected View m_customView;
    protected FrameLayout m_customViewContainer;
    protected View m_contentView;
    protected FSVideoChromeClient m_chromeClient;
    protected View m_fab;

	public void initialize(Article article) {
		m_article = article;
	}

    private class FSVideoChromeClient extends WebChromeClient {
        //protected View m_videoChildView;

        private CustomViewCallback m_callback;

        public FSVideoChromeClient(View container) {
            super();

        }

        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            m_activity.getSupportActionBar().hide();

            // if a view already exists then immediately terminate the new one
            if (m_customView != null) {
                callback.onCustomViewHidden();
                return;
            }
            m_customView = view;
            m_contentView.setVisibility(View.GONE);

            m_customViewContainer.setVisibility(View.VISIBLE);
            m_customViewContainer.addView(view);

            if (m_fab != null) m_fab.setVisibility(View.GONE);

            m_activity.showSidebar(false);

            m_callback = callback;
        }

        @Override
        public void onHideCustomView() {
            super.onHideCustomView();

            m_activity.getSupportActionBar().show();

            if (m_customView == null)
                return;

            m_contentView.setVisibility(View.VISIBLE);
            m_customViewContainer.setVisibility(View.GONE);

            // Hide the custom view.
            m_customView.setVisibility(View.GONE);

            // Remove the custom view from its container.
            m_customViewContainer.removeView(m_customView);
            m_callback.onCustomViewHidden();

            if (m_fab != null && m_prefs.getBoolean("enable_article_fab", true))
                m_fab.setVisibility(View.VISIBLE);

            m_customView = null;

            m_activity.showSidebar(true);
        }
    }

	//private View.OnTouchListener m_gestureListener;

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
	    ContextMenuInfo menuInfo) {

		if (v.getId() == R.id.article_content) {
			HitTestResult result = ((WebView)v).getHitTestResult();

			if (result != null && (result.getType() == HitTestResult.IMAGE_TYPE || result.getType() == HitTestResult.SRC_IMAGE_ANCHOR_TYPE)) {

				menu.setHeaderTitle(result.getExtra());
				getActivity().getMenuInflater().inflate(R.menu.article_content_img_context_menu, menu);
				
				/* FIXME I have no idea how to do this correctly ;( */
				
				m_activity.setLastContentImageHitTestUrl(result.getExtra());
				
			} else {
				menu.setHeaderTitle(m_article.title);
				getActivity().getMenuInflater().inflate(R.menu.article_link_context_menu, menu);
			}
		} else {
			menu.setHeaderTitle(m_article.title);
			getActivity().getMenuInflater().inflate(R.menu.article_link_context_menu, menu);
		}
		
		super.onCreateContextMenu(menu, v, menuInfo);		
		
	}
	
	@SuppressLint("NewApi")
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {    	

		if (savedInstanceState != null) {
			m_article = savedInstanceState.getParcelable("article");
            //m_fsviewShown = savedInstanceState.getBoolean("fsviewShown");
		}

		boolean useTitleWebView = m_prefs.getBoolean("article_compat_view", false);
		
		View view = inflater.inflate(useTitleWebView ? R.layout.article_fragment_compat : R.layout.article_fragment, container, false);

        /* if (m_fsviewShown) {
            view.findViewById(R.id.article_fullscreen_video).setVisibility(View.VISIBLE);
            view.findViewById(R.id.article_scrollview).setVisibility(View.INVISIBLE);
        } */

        if (m_article != null) {

            m_contentView = view.findViewById(R.id.article_scrollview);
            m_customViewContainer = (FrameLayout) view.findViewById(R.id.article_fullscreen_video);

            View scrollView = view.findViewById(R.id.article_scrollview);
            m_fab = view.findViewById(R.id.article_fab);

            if (scrollView != null && m_fab != null) {
                if (m_prefs.getBoolean("enable_article_fab", true)) {
                    scrollView.setOnTouchListener(new ShowHideOnScroll(m_fab));

                    m_fab.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            try {
                                URL url = new URL(m_article.link.trim());
                                String uri = new URI(url.getProtocol(), url.getUserInfo(), url.getHost(),
                                        url.getPort(), url.getPath(), url.getQuery(), url.getRef()).toString();
                                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
                                startActivity(intent);
                            } catch (Exception e) {
                                e.printStackTrace();
                                m_activity.toast(R.string.error_other_error);
                            }
                        }
                    });
                } else {
                    m_fab.setVisibility(View.GONE);
                }
            }

			int articleFontSize = Integer.parseInt(m_prefs.getString("article_font_size_sp", "16"));
			int articleSmallFontSize = Math.max(10, Math.min(18, articleFontSize - 2));
			
			TextView title = (TextView)view.findViewById(R.id.title);
						
			if (title != null) {
				
				if (m_prefs.getBoolean("enable_condensed_fonts", false)) {
					Typeface tf = TypefaceCache.get(m_activity, "sans-serif-condensed", Typeface.NORMAL);
					
					if (tf != null && !tf.equals(title.getTypeface())) {
						title.setTypeface(tf);
					}
					
					title.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.min(21, articleFontSize + 5));
				} else {
					title.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.min(21, articleFontSize + 3));
				}
				
				String titleStr;
				
				if (m_article.title.length() > 200)
					titleStr = m_article.title.substring(0, 200) + "...";
				else
					titleStr = m_article.title;
								
				title.setText(Html.fromHtml(titleStr));
				//title.setPaintFlags(title.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
				title.setOnClickListener(new OnClickListener() {					
					@Override
					public void onClick(View v) {
						try {
							URL url = new URL(m_article.link.trim());
							String uri = new URI(url.getProtocol(), url.getUserInfo(), url.getHost(),
								url.getPort(), url.getPath(), url.getQuery(), url.getRef()).toString();
							Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
							startActivity(intent);
						} catch (Exception e) {
							e.printStackTrace();
							m_activity.toast(R.string.error_other_error);
						}
					}
				});
				
				registerForContextMenu(title); 
			}
			
			TextView comments = (TextView)view.findViewById(R.id.comments);
			
			if (comments != null) {
				if (m_activity.getApiLevel() >= 4 && m_article.comments_count > 0) {
					comments.setTextSize(TypedValue.COMPLEX_UNIT_SP, articleSmallFontSize);
					
					String commentsTitle = getResources().getQuantityString(R.plurals.article_comments, m_article.comments_count, m_article.comments_count);
					comments.setText(commentsTitle);
					//comments.setPaintFlags(title.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
					comments.setOnClickListener(new OnClickListener() {					
						@Override
						public void onClick(View v) {
							try {
								URL url = new URL((m_article.comments_link != null && m_article.comments_link.length() > 0) ?
									m_article.comments_link : m_article.link);
								String uri = new URI(url.getProtocol(), url.getUserInfo(), url.getHost(),
										url.getPort(), url.getPath(), url.getQuery(), url.getRef()).toString();
								Intent intent = new Intent(Intent.ACTION_VIEW, 
										Uri.parse(uri));
									startActivity(intent);
							} catch (Exception e) {
								e.printStackTrace();
								m_activity.toast(R.string.error_other_error);
							}
						}
					});
					
				} else {
					comments.setVisibility(View.GONE);					
				}
			}
			
			TextView note = (TextView)view.findViewById(R.id.note);
			
			if (note != null) {
				if (m_article.note != null && !"".equals(m_article.note)) {
					note.setTextSize(TypedValue.COMPLEX_UNIT_SP, articleSmallFontSize);
					note.setText(m_article.note);					
				} else {
					note.setVisibility(View.GONE);
				}
				
			}
			
			m_web = (WebView)view.findViewById(R.id.article_content);
			
			if (m_web != null) {
				
				m_web.setOnLongClickListener(new View.OnLongClickListener() {
					@Override
					public boolean onLongClick(View v) {
						HitTestResult result = ((WebView)v).getHitTestResult();

                        if (result != null && (result.getType() == HitTestResult.IMAGE_TYPE || result.getType() == HitTestResult.SRC_IMAGE_ANCHOR_TYPE)) {
							registerForContextMenu(m_web);
							m_activity.openContextMenu(m_web);
							unregisterForContextMenu(m_web);
							return true;
						} else {
							if (m_activity.isCompatMode()) {
								KeyEvent shiftPressEvent = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SHIFT_LEFT, 0, 0);
								shiftPressEvent.dispatch(m_web);
							}
							
							return false;
						}
					}
				});

                boolean acceleratedWebview = true;

			    // prevent flicker in ics
			    if (!m_prefs.getBoolean("webview_hardware_accel", true) || useTitleWebView) {
			    	if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB) {
			    		m_web.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
                        acceleratedWebview = false;
			    	}
			    }

				String content;
				String cssOverride = "";
				
				WebSettings ws = m_web.getSettings();
				ws.setSupportZoom(false);

				TypedValue tvBackground = new TypedValue();
			    getActivity().getTheme().resolveAttribute(R.attr.articleBackground, tvBackground, true);

                String backgroundHexColor = String.format("#%06X", (0xFFFFFF & tvBackground.data));

                cssOverride = "body { background : "+ backgroundHexColor+"; }";

                TypedValue tvTextColor = new TypedValue();
                getActivity().getTheme().resolveAttribute(R.attr.articleTextColor, tvTextColor, true);

                String textColor = String.format("#%06X", (0xFFFFFF & tvTextColor.data));

                cssOverride += "body { color : "+textColor+"; }";

                TypedValue tvLinkColor = new TypedValue();
                getActivity().getTheme().resolveAttribute(R.attr.linkColor, tvLinkColor, true);

				String linkHexColor = String.format("#%06X", (0xFFFFFF & tvLinkColor.data));
			    cssOverride += " a:link {color: "+linkHexColor+";} a:visited { color: "+linkHexColor+";}";

				String articleContent = m_article.content != null ? m_article.content : "";

                if (m_activity.isCompatMode() || !acceleratedWebview) {
                    Document doc = Jsoup.parse(articleContent);

                    if (doc != null) {
                        // thanks webview for crashing on <video> tag
                        Elements videos = doc.select("video");

                        for (Element video : videos)
                            video.remove();

                        videos = doc.select("iframe");

                        for (Element video : videos)
                            video.remove();

                        articleContent = doc.toString();
                    }
                } else {
                    if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                        ws.setJavaScriptEnabled(true);

                        m_chromeClient = new FSVideoChromeClient(view);
                        m_web.setWebChromeClient(m_chromeClient);
                    }
                }

				if (m_prefs.getBoolean("justify_article_text", true)) {
					cssOverride += "body { text-align : justify; } ";
				}

                //if (m_article.lang.equals("he"))
                if (BidiFormatter.getInstance().isRtl(m_article.title))
                    cssOverride += "body { direction: rtl; } ";

				ws.setDefaultFontSize(articleFontSize);

				content = 
					"<html>" +
					"<head>" +
					"<meta content=\"text/html; charset=utf-8\" http-equiv=\"content-type\">" +
					"<meta name=\"viewport\" content=\"width=device-width, user-scalable=no\" />" +
					"<style type=\"text/css\">" +
					"body { padding : 0px; margin : 0px; line-height : 130%; }" +
					"img, video, iframe { max-width : 100%; width : auto; height : auto; }" +
                    " table { width : 100%; }" +
					cssOverride +
					"</style>" +
					"</head>" +
					"<body>" + articleContent.replace("<html>", "").replace("<body>", "").replace("</body>","").replace("</html>","");
				
				if (useTitleWebView) {
					content += "<p>&nbsp;</p><p>&nbsp;</p><p>&nbsp;</p><p>&nbsp;</p>";
				}

                if (m_article.attachments != null && m_article.attachments.size() != 0) {
					String flatContent = articleContent.replaceAll("[\r\n]", "");
					boolean hasImages = flatContent.matches(".*?<img[^>+].*?");
					
					for (Attachment a : m_article.attachments) {
						if (a.content_type != null && a.content_url != null) {							
							try {
								if (a.content_type.indexOf("image") != -1 && 
										(!hasImages || m_article.always_display_attachments)) {
									
									URL url = new URL(a.content_url.trim());
									String strUrl = url.toString().trim();

									content += "<p><img src=\"" + strUrl.replace("\"", "\\\"") + "\"></p>";
								}

							} catch (MalformedURLException e) {
								//
							} catch (Exception e) {
								e.printStackTrace();
							}
						}					
					}
				}
				
				content += "</body></html>";
					
				try {
					String baseUrl = null;
					
					try {
						URL url = new URL(m_article.link);
						baseUrl = url.getProtocol() + "://" + url.getHost();
					} catch (MalformedURLException e) {
						//
					}

                    if (savedInstanceState == null || !acceleratedWebview) {
                        m_web.loadDataWithBaseURL(baseUrl, content, "text/html", "utf-8", null);
                    } else {
                        WebBackForwardList rc = m_web.restoreState(savedInstanceState);

                        if (rc == null) {
                            // restore failed...
                            m_web.loadDataWithBaseURL(baseUrl, content, "text/html", "utf-8", null);
                        }
                    }

				} catch (RuntimeException e) {					
					e.printStackTrace();
				}
				
//				if (m_activity.isSmallScreen())
//					web.setOnTouchListener(m_gestureListener);
				
				m_web.setVisibility(View.VISIBLE);
			}
			
			TextView dv = (TextView)view.findViewById(R.id.date);
			
			if (dv != null) {
				dv.setTextSize(TypedValue.COMPLEX_UNIT_SP, articleSmallFontSize);
				
				Date d = new Date(m_article.updated * 1000L);
				DateFormat df = new SimpleDateFormat("MMM dd, HH:mm");
				dv.setText(df.format(d));
			}

			TextView author = (TextView)view.findViewById(R.id.author);

			boolean hasAuthor = false;
			
			if (author != null) {
				author.setTextSize(TypedValue.COMPLEX_UNIT_SP, articleSmallFontSize);
				
				if (m_article.author != null && m_article.author.length() > 0) {
					author.setText(getString(R.string.author_formatted, m_article.author));				
				} else {
					author.setVisibility(View.GONE);
				}
				hasAuthor = true;
			}

			TextView tagv = (TextView)view.findViewById(R.id.tags);
						
			if (tagv != null) {
				tagv.setTextSize(TypedValue.COMPLEX_UNIT_SP, articleSmallFontSize);
				
				if (m_article.feed_title != null) {
					String fTitle = m_article.feed_title;
					
					if (!hasAuthor && m_article.author != null && m_article.author.length() > 0) {
						fTitle += " (" + getString(R.string.author_formatted, m_article.author) + ")";						
					}
					
					tagv.setText(fTitle);
				} else if (m_article.tags != null) {
					String tagsStr = "";
				
					for (String tag : m_article.tags)
						tagsStr += tag + ", ";
					
					tagsStr = tagsStr.replaceAll(", $", "");
				
					tagv.setText(tagsStr);
				} else {
					tagv.setVisibility(View.GONE);
				}
			}
			
		} 
		
		return view;    	
	}

    @Override
    public void onPause() {
        super.onPause();

        if (!m_activity.isCompatMode()) {
            m_web.onPause();
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (!m_activity.isCompatMode()) {
            m_web.onResume();
        }
    }

    public boolean inCustomView() {
        return (m_customView != null);
    }

    @Override
    public void onStop() {
        super.onStop();

        if (inCustomView()) {
            hideCustomView();
        }
    }

    public void hideCustomView() {
        if (m_chromeClient != null) {
            m_chromeClient.onHideCustomView();
        }
    }

	@Override
	public void onDestroy() {
		super.onDestroy();		
	}
	
	@Override
	public void onSaveInstanceState (Bundle out) {		
		super.onSaveInstanceState(out);

		out.setClassLoader(getClass().getClassLoader());
		out.putParcelable("article", m_article);
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);		
		
		m_prefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext());
		m_activity = (HeadlinesActivity)activity;

	}
}
