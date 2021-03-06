package net.fastfourier.something.request;

import android.content.Context;
import android.text.TextUtils;

import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Response;
import com.salvadordalvik.fastlibrary.util.FastUtils;
import net.fastfourier.something.data.ThreadManager;
import net.fastfourier.something.list.ThreadItem;
import net.fastfourier.something.util.MustCache;
import net.fastfourier.something.util.SomePreferences;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by matthewshepard on 1/19/14.
 */
public class ThreadPageRequest extends HTMLRequest<ThreadPageRequest.ThreadPage> {
    private long jumpToPost = 0;
    private Context context;

    public ThreadPageRequest(Context context, int threadId, int page, Response.Listener<ThreadPage> success, Response.ErrorListener error) {
        super("http://forums.somethingawful.com/showthread.php", Request.Method.GET, success, error);
        addParam("threadid", threadId);
        if(page > 0){
            addParam("pagenumber", page);
        }else if(page < 0){
            addParam("goto", "lastpost");
        }else{
            addParam("goto", "newpost");
        }
        addParam("perpage", SomePreferences.threadPostPerPage);
        this.context = context;
    }

    public ThreadPageRequest(Context context, long postId, Response.Listener<ThreadPage> success, Response.ErrorListener error) {
        super("http://forums.somethingawful.com/showthread.php", Request.Method.GET, success, error);
        addParam("postid", postId);
        addParam("goto", "post");
        addParam("perpage", SomePreferences.threadPostPerPage);
        this.jumpToPost = postId;
        this.context = context;
    }

    @Override
    public ThreadPage parseHtmlResponse(NetworkResponse response, Document document) throws Exception {
        return processThreadPage(document, SomePreferences.shouldShowImages(context), SomePreferences.shouldShowAvatars(context), SomePreferences.hidePreviouslyReadPosts, jumpToPost);
    }

    public static ThreadPage processThreadPage(Document document, boolean showImages, boolean showAvatars, boolean hidePreviouslyReadImages, long jumpToPost){
        ArrayList<HashMap<String, String>> posts = new ArrayList<HashMap<String, String>>();

        int currentPage, maxPage = 1, threadId, forumId, unread;

        unread = parsePosts(document, posts, showImages, showAvatars, hidePreviouslyReadImages);

        Element pages = document.getElementsByClass("pages").first();
        currentPage = FastUtils.safeParseInt(pages.getElementsByAttribute("selected").attr("value"), 1);
        Element lastPage = pages.getElementsByTag("option").last();
        if(lastPage != null){
            maxPage = FastUtils.safeParseInt(lastPage.attr("value"), 1);
        }

        boolean bookmarked = document.getElementsByClass("unbookmark").size() > 0;

        String threadTitle = document.getElementsByClass("bclast").first().text();

        Element body = document.getElementsByTag("body").first();
        forumId = Integer.parseInt(body.attr("data-forum"));
        threadId = Integer.parseInt(body.attr("data-thread"));

        StringBuilder builder = new StringBuilder(2048);

        int previouslyRead = posts.size()-unread;

        HashMap<String, String> headerArgs = new HashMap<String, String>();
        headerArgs.put("jumpToPostId", Long.toString(jumpToPost));
        headerArgs.put("fontSize", Integer.toString(SomePreferences.fontSize));
        headerArgs.put("theme", getTheme(forumId));
        headerArgs.put("previouslyRead", previouslyRead > 0 && unread > 0 ? previouslyRead+" Previous Post"+(previouslyRead > 1 ? "s":"") : null);
        MustCache.applyHeaderTemplate(builder, headerArgs);

        for(HashMap<String, String> post : posts){
            MustCache.applyPostTemplate(builder, post);
        }

        if(currentPage == maxPage){
            builder.append("<div class='unread'></div>");
        }

        MustCache.applyFooterTemplate(builder, null);

        ThreadItem cachedThread = ThreadManager.getThread(threadId);
        if(cachedThread != null){
            cachedThread.updateUnreadCount(currentPage, maxPage, SomePreferences.threadPostPerPage);
        }

        return new ThreadPage(builder.toString(), currentPage, maxPage, threadId, forumId, threadTitle, -unread, bookmarked);

    }

    private static String getTheme(int forumId){
        if(SomePreferences.forceTheme){
            return SomePreferences.selectedTheme;
        }else{
            switch (forumId){
                case 219:
                    return SomePreferences.yosTheme;
                case 26:
                    return SomePreferences.fyadTheme;
                default:
                    return SomePreferences.selectedTheme;
            }
        }
    }

    public static class ThreadPage{
        public final int pageNum, maxPageNum, threadId, forumId, unreadDiff;
        public final String threadTitle, pageHtml;
        public final boolean bookmarked;

        private ThreadPage(String pageHtml, int pageNum, int maxPageNum, int threadId, int forumId, String threadTitle, int unreadDiff, boolean bookmarked){
            this.pageHtml = pageHtml;
            this.pageNum = pageNum;
            this.maxPageNum = maxPageNum;
            this.threadId = threadId;
            this.forumId = forumId;
            this.threadTitle = threadTitle;
            this.unreadDiff = unreadDiff;
            this.bookmarked = bookmarked;
        }
    }

    private static Pattern userJumpPattern = Pattern.compile("userid=(\\d+)");

    private static int parsePosts(Document doc, ArrayList<HashMap<String, String>> postArray, boolean showImages, boolean showAvatars, boolean hideSeenImages){
        int unread = 0;
        Elements posts = doc.getElementsByClass("post");
        for(Element post : posts){
            String rawId = post.id().replaceAll("\\D", "");
            if(!TextUtils.isEmpty(rawId)){
                HashMap<String, String> postData = new HashMap<String, String>();
                postData.put("postID", rawId);
                String author = post.getElementsByClass("author").text();
                Element title = post.getElementsByClass("title").first();
                String avTitle = title.text();
                String avatarUrl = title.getElementsByTag("img").attr("src");
                String postDate = post.getElementsByClass("postdate").text().replaceAll("[#?]", "").trim();
                String postIndex = post.attr("data-idx");

                boolean editable = post.getElementsByAttributeValueContaining("href","editpost.php?action=editpost").size() > 0;

                Element userInfo = post.getElementsByClass("user_jump").first();
                Matcher userIdMatcher = userJumpPattern.matcher(userInfo.attr("href"));
                String userId = null;
                if(userIdMatcher.find()){
                    userId = userIdMatcher.group(1);
                }

                boolean previouslyRead = post.getElementsByClass("seen1").size() > 0 || post.getElementsByClass("seen2").size() > 0;
                if(!previouslyRead){
                    unread++;
                }

                Element postBody = post.getElementsByClass("postbody").first();
                if(!showImages){
                    for(Element imageNode : postBody.getElementsByTag("img")){
                        String src = imageNode.attr("src"), imgTitle = imageNode.attr("title");
                        if(TextUtils.isEmpty(imgTitle)){//only emotes have titles
                            imageNode.tagName("a");
                            imageNode.addClass("hiddenimg");
                            imageNode.attr("href", src);
                            imageNode.text(src);
                        }else{
                            imageNode.tagName("span");
                            imageNode.addClass("hiddenavatar");
                            imageNode.text(imgTitle);
                        }
                        imageNode.removeAttr("src");
                    }
                }else if(hideSeenImages && previouslyRead){
                    for(Element imageNode : postBody.getElementsByTag("img")){
                        imageNode.addClass("seenimg");
                        imageNode.attr("hideimg", imageNode.attr("src"));
                        imageNode.removeAttr("src");
                    }
                }
                String postContent = postBody.html();

                postData.put("username", author);
                postData.put("avatarText", avTitle);
                postData.put("avatarURL", ( showAvatars && avatarUrl != null &&  avatarUrl.length() > 0 ) ? avatarUrl : null);
                postData.put("postcontent", postContent);
                postData.put("postDate", postDate);
                postData.put("userID", userId);
                postData.put("seen", previouslyRead ? "read" : "unread");
                postData.put("postIndex",  postIndex);

//                postData.put("regDate", post.getRegDate());
//                postData.put("lastReadUrl",  post.getLastReadUrl());
                //TODO nullable, can wait to implement
//                postData.put("isOP", (aPrefs.highlightOP && post.isOp())?"op":null);
//                postData.put("isMarked", (aPrefs.markedUsers.contains(post.getUsername()))?"marked":null);
//                postData.put("isSelf", (aPrefs.highlightSelf && post.getUsername().equals(aPrefs.username)) ? "self" : null);
//                postData.put("notOnProbation", (aPrefs.isOnProbation())?null:"notOnProbation");
//                postData.put("editable", (post.isEditable())?"editable":null);
//                postData.put("mod", (post.isMod())?"mod":null);
//                postData.put("admin", (post.isAdmin())?"admin":null);

                postData.put("regDate", "");
                postData.put("isOP", null);
                postData.put("isMarked", null);
                postData.put("isSelf", null);
                postData.put("notOnProbation", "notOnProbation");
                postData.put("editable", editable ? "editable" : null);
                postData.put("mod", null);
                postData.put("admin", null);

                postArray.add(postData);
            }
        }
        return unread;
    }
}
