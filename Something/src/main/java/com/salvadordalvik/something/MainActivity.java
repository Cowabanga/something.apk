package com.salvadordalvik.something;

import android.app.Activity;

import android.app.ActionBar;
import android.app.FragmentTransaction;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.widget.SlidingPaneLayout;
import android.text.Spanned;
import android.view.MenuItem;
import android.view.View;

public class MainActivity extends Activity implements SlidingPaneLayout.PanelSlideListener {
    private SlidingPaneLayout slidingLayout;

    private ThreadListFragment threadList;
    private ThreadViewFragment threadView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        configureActionbar();
        configureSlidingLayout();
        threadView = (ThreadViewFragment) getFragmentManager().findFragmentById(R.id.threadview_fragment);
        threadList = new ThreadListFragment();
        getFragmentManager().beginTransaction().add(R.id.list_container, threadList, "thread_list").commit();
    }

    private void configureActionbar(){
        ActionBar bar = getActionBar();
        bar.setHomeButtonEnabled(true);
    }

    private void configureSlidingLayout(){
        slidingLayout = (SlidingPaneLayout) findViewById(R.id.sliding_layout);
        slidingLayout.setSliderFadeColor(Color.argb(0,0,0,0));
        slidingLayout.setShadowResource(R.drawable.right_divider);
        slidingLayout.setPanelSlideListener(this);
        slidingLayout.openPane();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()){
            case android.R.id.home:
                slidingLayout.openPane();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        if(!slidingLayout.isOpen()){
            slidingLayout.openPane();
        }else if(getFragmentManager().getBackStackEntryCount() > 0){
            getFragmentManager().popBackStack();
        }else{
            super.onBackPressed();
        }
    }

    @Override
    public void onPanelSlide(View view, float v) {

    }

    @Override
    public void onPanelOpened(View view) {
        Spanned title = threadList.getTitle();
        if(title != null && title.length() > 0){
            setTitle(title);
        }
    }

    @Override
    public void onPanelClosed(View view) {
        Spanned title = threadView.getTitle();
        if(title != null && title.length() > 0){
            setTitle(title);
        }
    }

    public void showThread(int id) {
        showThread(id, 0);
    }

    public void showThread(int id, int page) {
        slidingLayout.closePane();
        threadView.loadThread(id, page);
    }

    public void showForum(int id) {
        slidingLayout.openPane();
        if(getFragmentManager().getBackStackEntryCount() > 0){
            getFragmentManager().popBackStackImmediate();
        }
        threadList.showForum(id);
    }

    public void showForumList(){
        FragmentTransaction trans = getFragmentManager().beginTransaction();
        trans.replace(R.id.list_container, new ForumListFragment(), "forum_list");
        trans.addToBackStack("open_forum_list");
        trans.commit();
    }
}
