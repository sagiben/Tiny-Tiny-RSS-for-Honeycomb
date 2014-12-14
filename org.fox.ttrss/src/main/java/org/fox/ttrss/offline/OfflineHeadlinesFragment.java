package org.fox.ttrss.offline;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources.Theme;
import android.database.Cursor;
import android.database.sqlite.SQLiteStatement;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SimpleCursorAdapter;
import android.support.v4.widget.SwipeRefreshLayout;
import android.text.Html;
import android.util.Log;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.amulyakhare.textdrawable.TextDrawable;
import com.amulyakhare.textdrawable.util.ColorGenerator;

import org.fox.ttrss.CommonActivity;
import org.fox.ttrss.GlobalState;
import org.fox.ttrss.R;
import org.fox.ttrss.util.TypefaceCache;
import org.jsoup.Jsoup;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.TimeZone;

public class OfflineHeadlinesFragment extends Fragment implements OnItemClickListener, AbsListView.OnScrollListener {
	public static enum ArticlesSelection { ALL, NONE, UNREAD }

    private final String TAG = this.getClass().getSimpleName();
	
	private int m_feedId;
	private boolean m_feedIsCat = false;
	private int m_activeArticleId;
	private String m_searchQuery = "";
	
	private SharedPreferences m_prefs;
    private ArrayList<Integer> m_readArticleIds = new ArrayList<Integer>();
	
	private Cursor m_cursor;
	private ArticleListAdapter m_adapter;
	
	private OfflineHeadlinesEventListener m_listener;
	private OfflineActivity m_activity;
	private SwipeRefreshLayout m_swipeLayout;

    private boolean m_compactLayoutMode = false;
	
	public void initialize(int feedId, boolean isCat, boolean compactMode) {
		m_feedId = feedId;
		m_feedIsCat = isCat;
        m_compactLayoutMode = compactMode;
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		
		if (m_cursor != null && !m_cursor.isClosed()) m_cursor.close();
	}
	
	public int getSelectedArticleCount() {
		Cursor c = m_activity.getReadableDb().query("articles", 
				new String[] { "COUNT(*)" }, "selected = 1", null, null, null, null);
		c.moveToFirst();
		int selected = c.getInt(0);
		c.close();
		
		return selected;
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item
				.getMenuInfo();
		
		switch (item.getItemId()) {
		case R.id.article_link_copy:
			if (true) {
				int articleId = getArticleIdAtPosition(info.position);
				
				Cursor article = m_activity.getArticleById(articleId);
				
				if (article != null) {				
					m_activity.copyToClipboard(article.getString(article.getColumnIndex("link")));
					article.close();
				}
			}
			return true;
		case R.id.selection_toggle_marked:
			if (getSelectedArticleCount() > 0) {
				SQLiteStatement stmt = m_activity.getWritableDb()
				.compileStatement(
						"UPDATE articles SET modified = 1, marked = NOT marked WHERE selected = 1");
				stmt.execute();
				stmt.close();
			} else {
				int articleId = getArticleIdAtPosition(info.position);
				
				SQLiteStatement stmt = m_activity.getWritableDb().compileStatement(
					"UPDATE articles SET modified = 1, marked = NOT marked WHERE "
							+ BaseColumns._ID + " = ?");
				stmt.bindLong(1, articleId);
				stmt.execute();
				stmt.close();
			}
			refresh();
			return true;
		case R.id.selection_toggle_published:
			if (getSelectedArticleCount() > 0) {
				SQLiteStatement stmt = m_activity.getWritableDb()
				.compileStatement(
						"UPDATE articles SET modified = 1, published = NOT published WHERE selected = 1");
				stmt.execute();
				stmt.close();
			} else {
				int articleId = getArticleIdAtPosition(info.position);
				
				SQLiteStatement stmt = m_activity.getWritableDb().compileStatement(
					"UPDATE articles SET modified = 1, published = NOT published WHERE "
							+ BaseColumns._ID + " = ?");
				stmt.bindLong(1, articleId);
				stmt.execute();
				stmt.close();
			}
			refresh();
			return true;
		case R.id.selection_toggle_unread:
			if (getSelectedArticleCount() > 0) {
				SQLiteStatement stmt = m_activity.getWritableDb()
				.compileStatement(
						"UPDATE articles SET modified = 1, unread = NOT unread WHERE selected = 1");
				stmt.execute();
				stmt.close();
			} else {
				int articleId = getArticleIdAtPosition(info.position);
				
				SQLiteStatement stmt = m_activity.getWritableDb().compileStatement(
					"UPDATE articles SET modified = 1, unread = NOT unread WHERE "
							+ BaseColumns._ID + " = ?");
				stmt.bindLong(1, articleId);
				stmt.execute();
				stmt.close();
			}
			refresh();			
			return true;
		case R.id.share_article:
			if (true) {
				int articleId = getArticleIdAtPosition(info.position);
				m_activity.shareArticle(articleId);
			}
			return true;
		case R.id.catchup_above:
			if (true) {
				int articleId = getArticleIdAtPosition(info.position);
				
				SQLiteStatement stmt = null;
				
				String updatedOperator = (m_prefs.getBoolean("offline_oldest_first", false)) ? "<" : ">";
				
				if (m_feedIsCat) {
					stmt = m_activity.getWritableDb().compileStatement(
							"UPDATE articles SET modified = 1, unread = 0 WHERE " +
							"updated "+updatedOperator+" (SELECT updated FROM articles WHERE " + BaseColumns._ID + " = ?) " +
							"AND unread = 1 AND feed_id IN (SELECT "+BaseColumns._ID+" FROM feeds WHERE cat_id = ?)");						
				} else {
					stmt = m_activity.getWritableDb().compileStatement(
							"UPDATE articles SET modified = 1, unread = 0 WHERE " +
							"updated "+updatedOperator+" (SELECT updated FROM articles WHERE " + BaseColumns._ID + " = ?) " +
							"AND unread = 1 AND feed_id = ?");						
				}
				
				stmt.bindLong(1, articleId);
				stmt.bindLong(2, m_feedId);
				stmt.execute();
				stmt.close();
			}			
			refresh();
			return true;
		default:
			Log.d(TAG, "onContextItemSelected, unhandled id=" + item.getItemId());
			return super.onContextItemSelected(item);
		}
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
	    ContextMenuInfo menuInfo) {
		
		getActivity().getMenuInflater().inflate(R.menu.headlines_context_menu, menu);
		
		if (getSelectedArticleCount() > 0) {
			menu.setHeaderTitle(R.string.headline_context_multiple);
			menu.setGroupVisible(R.id.menu_group_single_article, false);
		} else {
			AdapterContextMenuInfo info = (AdapterContextMenuInfo)menuInfo;
			Cursor c = getArticleAtPosition(info.position);
			menu.setHeaderTitle(c.getString(c.getColumnIndex("title")));
			//c.close();
			menu.setGroupVisible(R.id.menu_group_single_article, true);
			
			menu.findItem(R.id.set_labels).setVisible(false);
			menu.findItem(R.id.article_set_note).setVisible(false);
		}
		
		super.onCreateContextMenu(menu, v, menuInfo);		
		
	}
	
	@Override
	public void onResume() {
		super.onResume();
		
		if (GlobalState.getInstance().m_selectedArticleId != 0) {			
			m_activeArticleId = GlobalState.getInstance().m_selectedArticleId;
			GlobalState.getInstance().m_selectedArticleId = 0;
		}

		if (m_activeArticleId != 0) {
			setActiveArticleId(m_activeArticleId);
		}

		refresh();
		
		m_activity.invalidateOptionsMenu();
	}
	
	public void refresh() {
		try {
			if (!isAdded()) return;

            if (m_swipeLayout != null) m_swipeLayout.setRefreshing(true);
			
			if (m_cursor != null && !m_cursor.isClosed()) m_cursor.close();
			
			m_cursor = createCursor();
			
			if (m_cursor != null && m_adapter != null) {
				m_adapter.changeCursor(m_cursor);
				m_adapter.notifyDataSetChanged();
			}

            if (m_swipeLayout != null) m_swipeLayout.setRefreshing(false);
			
		} catch (NullPointerException e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {    	
		
		if (savedInstanceState != null) {
			m_feedId = savedInstanceState.getInt("feedId");
			m_activeArticleId = savedInstanceState.getInt("activeArticleId");
			//m_selectedArticles = savedInstanceState.getParcelableArrayList("selectedArticles");
			m_searchQuery = (String) savedInstanceState.getCharSequence("searchQuery");
			m_feedIsCat = savedInstanceState.getBoolean("feedIsCat");
            m_compactLayoutMode = savedInstanceState.getBoolean("compactLayoutMode");
		} else {
			m_activity.getWritableDb().execSQL("UPDATE articles SET selected = 0 ");
		}

        if ("HL_COMPACT".equals(m_prefs.getString("headline_mode", "HL_DEFAULT")))
            m_compactLayoutMode = true;

		View view = inflater.inflate(R.layout.headlines_fragment, container, false);

		m_swipeLayout = (SwipeRefreshLayout) view.findViewById(R.id.headlines_swipe_container);
		
	    m_swipeLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
			@Override
			public void onRefresh() {
				refresh();
			}
		});

	    if (!m_activity.isCompatMode()) {
	    	m_swipeLayout.setColorScheme(android.R.color.holo_green_dark, 
	    		android.R.color.holo_red_dark, 
	            android.R.color.holo_blue_dark, 
	            android.R.color.holo_orange_dark);
	    }
		
		m_cursor = createCursor();
		
		ListView list = (ListView)view.findViewById(R.id.headlines_list);

        if (!m_compactLayoutMode) {
            list.setDividerHeight(0);
            list.setDivider(null);
        }

        if (m_prefs.getBoolean("headlines_mark_read_scroll", false)) {
            WindowManager wm = (WindowManager) m_activity.getSystemService(Context.WINDOW_SERVICE);
            Display display = wm.getDefaultDisplay();
            int screenHeight = display.getHeight();

            View layout = inflater.inflate(R.layout.headlines_footer, container, false);

            layout.setLayoutParams(new ListView.LayoutParams(ListView.LayoutParams.MATCH_PARENT, screenHeight));

            list.addFooterView(layout, null, false);
        }


        m_adapter = new ArticleListAdapter(getActivity(), R.layout.headlines_row, m_cursor,
				new String[] { "title" }, new int[] { R.id.title }, 0);
		
		list.setAdapter(m_adapter);
		list.setOnItemClickListener(this);
        list.setOnScrollListener(this);
		//list.setEmptyView(view.findViewById(R.id.no_headlines));
		registerForContextMenu(list);

		return view;    	
	}

	public Cursor createCursor() {
		String feedClause = null;
		
		if (m_feedIsCat) {
			feedClause = "feed_id IN (SELECT "+BaseColumns._ID+" FROM feeds WHERE cat_id = ?)";
		} else {
			feedClause = "feed_id = ?";
		}
		
		String viewMode = m_activity.getViewMode();
		
		if ("adaptive".equals(viewMode)) {
			// TODO: implement adaptive			
		} else if ("marked".equals(viewMode)) {
			feedClause += "AND (marked = 1)";
		} else if ("published".equals(viewMode)) {
			feedClause += "AND (published = 1)";
		} else if ("unread".equals(viewMode)) {
			feedClause += "AND (unread = 1)";
		} else { // all_articles
			//
		}
		
		String orderBy = (m_prefs.getBoolean("offline_oldest_first", false)) ? "updated" : "updated DESC";
		
		if (m_searchQuery == null || m_searchQuery.equals("")) {
			return m_activity.getReadableDb().query("articles LEFT JOIN feeds ON (feed_id = feeds."+BaseColumns._ID+")", 
					new String[] { "articles.*", "feeds.title AS feed_title" }, feedClause, 
					new String[] { String.valueOf(m_feedId) }, null, null, orderBy);
		} else {
			return m_activity.getReadableDb().query("articles LEFT JOIN feeds ON (feed_id = feeds."+BaseColumns._ID+")", 
					new String[] { "articles.*", "feeds.title AS feed_title" },
					feedClause + " AND (articles.title LIKE '%' || ? || '%' OR content LIKE '%' || ? || '%')", 
					new String[] { String.valueOf(m_feedId), m_searchQuery, m_searchQuery }, null, null, orderBy);
		}
	}
	
	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		m_listener = (OfflineHeadlinesEventListener) activity;
		m_activity = (OfflineActivity) activity;
		
		m_prefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext());
	}

	@Override
	public void onItemClick(AdapterView<?> av, View view, int position, long id) {
		ListView list = (ListView)av;
		
		Log.d(TAG, "onItemClick=" + position);
		
		if (list != null) {

			int articleId = getArticleIdAtPosition(position);

            if (articleId != 0) {

                if (getActivity().findViewById(R.id.article_fragment) != null) {
                    m_activeArticleId = articleId;
                }

                m_listener.onArticleSelected(articleId);

                refresh();
            }
		}
	}

	@Override
	public void onSaveInstanceState (Bundle out) {
		super.onSaveInstanceState(out);
		
		out.putInt("feedId", m_feedId);
		out.putInt("activeArticleId", m_activeArticleId);
		//out.putParcelableArrayList("selectedArticles", m_selectedArticles);
		out.putCharSequence("searchQuery", m_searchQuery);
		out.putBoolean("feedIsCat", m_feedIsCat);

        out.putBoolean("compactLayoutMode", m_compactLayoutMode);
	}

	/* public void setLoadingStatus(int status, boolean showProgress) {
		if (getView() != null) {
			TextView tv = (TextView)getView().findViewById(R.id.loading_message);
			
			if (tv != null) {
				tv.setText(status);
			}	
		}
		
		getActivity().setProgressBarIndeterminateVisibility(showProgress);
	} */

    static class HeadlineViewHolder {
        public TextView titleView;
        public TextView feedTitleView;
        public ImageView markedView;
        public ImageView publishedView;
        public TextView excerptView;
        public ImageView flavorImageView;
        public TextView authorView;
        public TextView dateView;
        public CheckBox selectionBoxView;
        public ImageView menuButtonView;
        public ViewGroup flavorImageHolder;
        public ProgressBar flavorImageLoadingBar;
        public View flavorImageArrow;
        public View headlineFooter;
        public ImageView textImage;
        public ImageView textChecked;
    }

    private class ArticleListAdapter extends SimpleCursorAdapter {
		public static final int VIEW_NORMAL = 0;
		public static final int VIEW_UNREAD = 1;
		public static final int VIEW_SELECTED = 2;
		public static final int VIEW_SELECTED_UNREAD = 3;
		public static final int VIEW_LOADMORE = 4;
		
		public static final int VIEW_COUNT = VIEW_LOADMORE+1;
		
		private final Integer[] origTitleColors = new Integer[VIEW_COUNT];
		private final int titleHighScoreUnreadColor;

        private ColorGenerator m_colorGenerator = ColorGenerator.DEFAULT;
        private TextDrawable.IBuilder m_drawableBuilder = TextDrawable.builder().round();
		
		public ArticleListAdapter(Context context, int layout, Cursor c,
				String[] from, int[] to, int flags) {
			super(context, layout, c, from, to, flags);
			
			Theme theme = context.getTheme();
			TypedValue tv = new TypedValue();
			theme.resolveAttribute(R.attr.headlineTitleHighScoreUnreadTextColor, tv, true);
			titleHighScoreUnreadColor = tv.data;
		}

		public int getViewTypeCount() {
			return VIEW_COUNT;
		}

		@Override
		public int getItemViewType(int position) {
			Cursor c = (Cursor) getItem(position);
			
			//Log.d(TAG, "@gIVT " + position + " " + c.getInt(0) + " vs " + m_activeArticleId);
			
			if (c.getInt(0) == m_activeArticleId && c.getInt(c.getColumnIndex("unread")) == 1) {				
				return VIEW_SELECTED_UNREAD;
			} else if (c.getInt(0) == m_activeArticleId) {
				return VIEW_SELECTED;
			} else if (c.getInt(c.getColumnIndex("unread")) == 1) {
				return VIEW_UNREAD;
			} else {
				return VIEW_NORMAL;				
			}			
		}

        private void updateTextCheckedState(HeadlineViewHolder holder, Cursor item) {
            String title = item.getString(item.getColumnIndex("title"));

            String tmp = title.length() > 0 ? title.substring(0, 1) : "?";

            boolean isChecked = item.getInt(item.getColumnIndex("selected")) == 1;

            if (isChecked) {
                holder.textImage.setImageDrawable(m_drawableBuilder.build(" ", 0xff616161));

                holder.textChecked.setVisibility(View.VISIBLE);
            } else {
                holder.textImage.setImageDrawable(m_drawableBuilder.build(tmp, m_colorGenerator.getColor(title)));

                holder.textChecked.setVisibility(View.GONE);
            }
        }

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {

			View v = convertView;

			final Cursor article = (Cursor)getItem(position);

            final HeadlineViewHolder holder;

			final int articleId = article.getInt(0);
			
			int headlineFontSize = Integer.parseInt(m_prefs.getString("headlines_font_size_sp", "13"));
			int headlineSmallFontSize = Math.max(10, Math.min(18, headlineFontSize - 2));
			
			if (v == null) {
                int layoutId = m_compactLayoutMode ? R.layout.headlines_row_compact : R.layout.headlines_row;

                switch (getItemViewType(position)) {
                    case VIEW_LOADMORE:
                        layoutId = R.layout.headlines_row_loadmore;
                        break;
                    case VIEW_UNREAD:
                        layoutId = m_compactLayoutMode ? R.layout.headlines_row_unread_compact : R.layout.headlines_row_unread;
                        break;
                    case VIEW_SELECTED:
                        layoutId = m_compactLayoutMode ? R.layout.headlines_row_selected_compact : R.layout.headlines_row_selected;
                        break;
                    case VIEW_SELECTED_UNREAD:
                        layoutId = m_compactLayoutMode ? R.layout.headlines_row_selected_unread_compact : R.layout.headlines_row_selected_unread;
                        break;
                }

                LayoutInflater vi = (LayoutInflater)getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                v = vi.inflate(layoutId, null);

                holder = new HeadlineViewHolder();
                holder.titleView = (TextView)v.findViewById(R.id.title);

                holder.feedTitleView = (TextView)v.findViewById(R.id.feed_title);
                holder.markedView = (ImageView)v.findViewById(R.id.marked);
                holder.publishedView = (ImageView)v.findViewById(R.id.published);
                holder.excerptView = (TextView)v.findViewById(R.id.excerpt);
                holder.flavorImageView = (ImageView) v.findViewById(R.id.flavor_image);
                holder.authorView = (TextView)v.findViewById(R.id.author);
                holder.dateView = (TextView) v.findViewById(R.id.date);
                holder.selectionBoxView = (CheckBox) v.findViewById(R.id.selected);
                holder.menuButtonView = (ImageView) v.findViewById(R.id.article_menu_button);
                holder.flavorImageHolder = (ViewGroup) v.findViewById(R.id.flavorImageHolder);
                holder.flavorImageLoadingBar = (ProgressBar) v.findViewById(R.id.flavorImageLoadingBar);
                holder.flavorImageArrow = v.findViewById(R.id.flavorImageArrow);
                holder.headlineFooter = v.findViewById(R.id.headline_footer);
                holder.textImage = (ImageView) v.findViewById(R.id.text_image);
                holder.textChecked = (ImageView) v.findViewById(R.id.text_checked);

                v.setTag(holder);

                // http://code.google.com/p/android/issues/detail?id=3414
                ((ViewGroup)v).setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            } else {
                holder = (HeadlineViewHolder) v.getTag();
            }

            // block footer clicks to make button/selection clicking easier
            if (holder.headlineFooter != null) {
                holder.headlineFooter.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        //
                    }
                });
            }

            if (holder.textImage != null) {
                updateTextCheckedState(holder, article);

                holder.textImage.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Log.d(TAG, "textImage : onclicked");

                        SQLiteStatement stmtUpdate = m_activity.getWritableDb().compileStatement("UPDATE articles SET selected = NOT selected " +
                                "WHERE " + BaseColumns._ID + " = ?");

                        stmtUpdate.bindLong(1, articleId);
                        stmtUpdate.execute();
                        stmtUpdate.close();

                        updateTextCheckedState(holder, article);

                        refresh();

                        m_activity.invalidateOptionsMenu();
                    }
                });

            }

			if (holder.titleView != null) {
				
				holder.titleView.setText(Html.fromHtml(article.getString(article.getColumnIndex("title"))));

				if (m_prefs.getBoolean("enable_condensed_fonts", false)) {
					Typeface tf = TypefaceCache.get(m_activity, "sans-serif-condensed", article.getInt(article.getColumnIndex("unread")) == 1 ? Typeface.BOLD : Typeface.NORMAL);
					
					if (tf != null && !tf.equals(holder.titleView.getTypeface())) {
						holder.titleView.setTypeface(tf);
					}

                    holder.titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.min(21, headlineFontSize + 5));
				} else {
                    holder.titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.min(21, headlineFontSize + 3));
				}
				
				int scoreIndex = article.getColumnIndex("score");
				if (scoreIndex >= 0)
					adjustTitleTextView(article.getInt(scoreIndex), holder.titleView, position);
			}
			
			int feedTitleIndex = article.getColumnIndex("feed_title");
			
			if (holder.feedTitleView != null && feedTitleIndex != -1 && m_feedIsCat) {				
				String feedTitle = article.getString(feedTitleIndex);
				
				if (feedTitle.length() > 20)
					feedTitle = feedTitle.substring(0, 20) + "...";
				
				if (feedTitle.length() > 0) {
					holder.feedTitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, headlineSmallFontSize);
					holder.feedTitleView.setText(feedTitle);					
				} else {
					holder.feedTitleView.setVisibility(View.GONE);
				}				
			} else if (holder.feedTitleView != null) {
				holder.feedTitleView.setVisibility(View.GONE);
			}
			
			if (holder.markedView != null) {
				holder.markedView.setImageResource(article.getInt(article.getColumnIndex("marked")) == 1 ? R.drawable.ic_star_full : R.drawable.ic_star_empty);
				
				holder.markedView.setOnClickListener(new OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        SQLiteStatement stmtUpdate = m_activity.getWritableDb().compileStatement("UPDATE articles SET modified = 1, marked = NOT marked " +
                                "WHERE " + BaseColumns._ID + " = ?");

                        stmtUpdate.bindLong(1, articleId);
                        stmtUpdate.execute();
                        stmtUpdate.close();

                        refresh();
                    }
                });
			}
			
			if (holder.publishedView != null) {
				holder.publishedView.setImageResource(article.getInt(article.getColumnIndex("published")) == 1 ? R.drawable.ic_published : R.drawable.ic_unpublished);
				
				holder.publishedView.setOnClickListener(new OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        SQLiteStatement stmtUpdate = m_activity.getWritableDb().compileStatement("UPDATE articles SET modified = 1, published = NOT published " +
                                "WHERE " + BaseColumns._ID + " = ?");

                        stmtUpdate.bindLong(1, articleId);
                        stmtUpdate.execute();
                        stmtUpdate.close();

                        refresh();
                    }
                });
			}

			if (holder.excerptView != null) {
				if (!m_prefs.getBoolean("headlines_show_content", true)) {
					holder.excerptView.setVisibility(View.GONE);
				} else {
                    String articleContent = article.getString(article.getColumnIndex("content"));

                    String tmp = articleContent.length() > CommonActivity.EXCERPT_MAX_QUERY_LENGTH ?
                            articleContent.substring(0, CommonActivity.EXCERPT_MAX_QUERY_LENGTH) : articleContent;

                    String excerpt = Jsoup.parse(tmp).text();

                    if (excerpt.length() > CommonActivity.EXCERPT_MAX_LENGTH) excerpt = excerpt.substring(0, CommonActivity.EXCERPT_MAX_LENGTH) + "…";

					holder.excerptView.setTextSize(TypedValue.COMPLEX_UNIT_SP, headlineFontSize);
					holder.excerptView.setText(excerpt);
				}
			}       	

			if (holder.authorView != null) {
				int authorIndex = article.getColumnIndex("author");
				if (authorIndex >= 0) {
					String author = article.getString(authorIndex);
					
					holder.authorView.setTextSize(TypedValue.COMPLEX_UNIT_SP, headlineSmallFontSize);					
					
					if (author != null && author.length() > 0) 
						holder.authorView.setText(getString(R.string.author_formatted, author));
					else
						holder.authorView.setText("");
				}
			}
			
			if (holder.dateView != null) {
				holder.dateView.setTextSize(TypedValue.COMPLEX_UNIT_SP, headlineSmallFontSize);
				
				Date d = new Date((long)article.getInt(article.getColumnIndex("updated")) * 1000);
				DateFormat df = new SimpleDateFormat("MMM dd, HH:mm");
				df.setTimeZone(TimeZone.getDefault());
				holder.dateView.setText(df.format(d));
			}

			if (holder.selectionBoxView != null) {
				holder.selectionBoxView.setChecked(article.getInt(article.getColumnIndex("selected")) == 1);
				holder.selectionBoxView.setOnClickListener(new OnClickListener() {

                    @Override
                    public void onClick(View view) {
                        CheckBox cb = (CheckBox) view;

                        SQLiteStatement stmtUpdate = m_activity.getWritableDb().compileStatement("UPDATE articles SET selected = ? " +
                                "WHERE " + BaseColumns._ID + " = ?");

                        stmtUpdate.bindLong(1, cb.isChecked() ? 1 : 0);
                        stmtUpdate.bindLong(2, articleId);
                        stmtUpdate.execute();
                        stmtUpdate.close();

                        refresh();

                        m_activity.invalidateOptionsMenu();

                    }
                });
			}

            if (holder.flavorImageHolder != null) {
                holder.flavorImageHolder.setVisibility(View.GONE);
            }
			
			if (holder.menuButtonView != null) {
				//if (m_activity.isDarkTheme())
				//	ib.setImageResource(R.drawable.ic_mailbox_collapsed_holo_dark);
				
				holder.menuButtonView.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        getActivity().openContextMenu(v);
                    }
                });								
			}

            return v;
		}

		private void adjustTitleTextView(int score, TextView tv, int position) {
			int viewType = getItemViewType(position);
			if (origTitleColors[viewType] == null)
				// store original color
				origTitleColors[viewType] = Integer.valueOf(tv.getCurrentTextColor());

			if (score < -500) {
				tv.setPaintFlags(tv.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
			} else if (score > 500) {
				tv.setTextColor(titleHighScoreUnreadColor);
				tv.setPaintFlags(tv.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
			} else {
				tv.setTextColor(origTitleColors[viewType].intValue());
				tv.setPaintFlags(tv.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
			}
		}
	}

	public void notifyUpdated() {
		m_adapter.notifyDataSetChanged();
	}

	public void setActiveArticleId(int articleId) {
		m_activeArticleId = articleId;
		try {
			m_adapter.notifyDataSetChanged();

			ListView list = (ListView)getView().findViewById(R.id.headlines_list);
		
			Log.d(TAG, articleId + " position " + getArticleIdPosition(articleId));
			
			if (list != null) {
				list.setSelection(getArticleIdPosition(articleId));
			}
		} catch (NullPointerException e) {
			// invoked before view is created, nvm
		}
	}

	public Cursor getArticleAtPosition(int position) {
		return (Cursor) m_adapter.getItem(position);
	}

	public int getArticleIdAtPosition(int position) {
		/*Cursor c = getArticleAtPosition(position);
		
		if (c != null) {
			int id = c.getInt(0);
			return id;
		}		*/
		
		return (int) m_adapter.getItemId(position);
	}

	public int getActiveArticleId() {
		return m_activeArticleId;
	}

	public int getArticleIdPosition(int articleId) {
		for (int i = 0; i < m_adapter.getCount(); i++) {
			if (articleId == m_adapter.getItemId(i))
				return i;
		}
		
		return -1;
	}
	
	public int getArticleCount() {
		return m_adapter.getCount();
	}

	public void setSearchQuery(String query) {
		if (!m_searchQuery.equals(query)) {
			m_searchQuery = query;
		}
	}

	public int getFeedId() {
		return m_feedId;
	}
	
	public boolean getFeedIsCat() {
		return m_feedIsCat;
	}

	public String getSearchQuery() {
		return m_searchQuery;
	}

    @Override
    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
        if (m_prefs.getBoolean("headlines_mark_read_scroll", false) && firstVisibleItem > 0) {
            //Article a = m_articles.get(firstVisibleItem - 1);

            Cursor article = getArticleAtPosition(firstVisibleItem - 1);

            if (article != null) {
                Integer id = article.getInt(article.getColumnIndex(BaseColumns._ID));
                boolean unread = article.getInt(article.getColumnIndex("unread")) != 0;

                if (unread && !m_readArticleIds.contains(id)) {
                    m_readArticleIds.add(id);
                }
            }
        }
    }

    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {
        if (scrollState == SCROLL_STATE_IDLE && m_prefs.getBoolean("headlines_mark_read_scroll", false)) {
            if (!m_readArticleIds.isEmpty()) {

                SQLiteStatement stmt = m_activity.getWritableDb().compileStatement(
                        "UPDATE articles SET modified = 1, unread = 0 " + "WHERE " + BaseColumns._ID
                                + " = ?");

                for (int articleId : m_readArticleIds) {
                    stmt.bindLong(1, articleId);
                    stmt.execute();
                }

                stmt.close();
                refresh();

                m_readArticleIds.clear();
            }
        }
    }

}
