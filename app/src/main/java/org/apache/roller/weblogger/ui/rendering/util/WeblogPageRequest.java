/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  The ASF licenses this file to You
 * under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.  For additional information regarding
 * copyright in this work, please see the NOTICE file in the top level
 * directory of this distribution.
 */

package org.apache.roller.weblogger.ui.rendering.util;

import static io.github.pixee.security.Newlines.stripAll;
import java.util.Collections;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.roller.weblogger.WebloggerException;
import org.apache.roller.weblogger.business.WeblogEntryManager;
import org.apache.roller.weblogger.business.WebloggerFactory;
import org.apache.roller.weblogger.config.WebloggerConfig;
import org.apache.roller.weblogger.pojos.ThemeTemplate;
import org.apache.roller.weblogger.pojos.WeblogCategory;
import org.apache.roller.weblogger.pojos.WeblogEntry;
import org.apache.roller.weblogger.pojos.WeblogTemplate;
import org.apache.roller.weblogger.util.URLUtilities;
import org.apache.roller.weblogger.util.Utilities;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a request for a Roller weblog page.
 * 
 * any url from ... /roller-ui/rendering/page/*
 * 
 * We use this class as a helper to parse an incoming url and sort out the
 * information embedded in the url for later use.
 */
public class WeblogPageRequest extends WeblogRequest {

    private static Log log = LogFactory.getLog(WeblogPageRequest.class);

    private static final String PAGE_SERVLET = "/roller-ui/rendering/page";

    // lightweight attributes
    private String context = null;
    private String weblogAnchor = null;
    private String weblogPageName = null;
    private String weblogCategoryName = null;
    private String weblogDate = null;
    private List<String> tags = null;
    private int pageNum = 0;
    private Map<String, String[]> customParams = Collections.emptyMap();

    // heavyweight attributes
    private WeblogEntry weblogEntry = null;
    private ThemeTemplate weblogPage = null;
    private WeblogCategory weblogCategory = null;

    // Page hits
    private boolean websitePageHit = false;
    private boolean otherPageHit = false;

    public WeblogPageRequest() {
    }

    /**
     * Construct the WeblogPageRequest by parsing the incoming url
     */
    public WeblogPageRequest(HttpServletRequest request)
            throws InvalidRequestException {

        // let our parent take care of their business first
        // parent determines weblog handle and locale if specified
        super(request);

        String servlet = request.getServletPath();

        // we only want the path info left over from after our parents parsing
        String pathInfo = this.getPathInfo();

        // parse the request object and figure out what we've got
        log.debug("parsing path " + stripAll(pathInfo));

        // was this request bound for the right servlet?
        if (!isValidDestination(servlet)) {
            throw new InvalidRequestException(
                    "invalid destination for request, "
                            + request.getRequestURL());
        }

        /*
         * parse path info
         * 
         * we expect one of the following forms of url ...
         * 
         * /entry/<anchor> - permalink /date/<YYYYMMDD> - date collection view
         * /category/<category> - category collection view /tags/<tag>+<tag> -
         * tags /page/<pagelink> - custom page
         * 
         * path info may be null, which indicates the weblog homepage
         */
        if (pathInfo != null && !pathInfo.isBlank()) {

            // all views use 2 path elements
            String[] pathElements = pathInfo.split("/", 2);

            // the first part of the path always represents the context
            this.context = pathElements[0];

            // now check the rest of the path and extract other details
            if (pathElements.length == 2) {

                if ("entry".equals(this.context)) {
                    this.weblogAnchor = URLUtilities.decode(pathElements[1]);

                    // Other page
                    otherPageHit = true;

                } else if ("date".equals(this.context)) {
                    if (this.isValidDateString(pathElements[1])) {
                        this.weblogDate = pathElements[1];
                    } else {
                        throw new InvalidRequestException("invalid date, "
                                + request.getRequestURL());
                    }

                    // Other page
                    otherPageHit = true;

                } else if ("category".equals(this.context)) {
                    this.weblogCategoryName = URLUtilities
                            .decode(pathElements[1]);

                    // Other page
                    otherPageHit = true;

                } else if ("page".equals(this.context)) {
                    this.weblogPageName = pathElements[1];
                    String tagsString = request.getParameter("tags");
                    if (tagsString != null) {
                        this.tags = Utilities.splitStringAsTags(URLUtilities
                                .decode(tagsString));
                    }

                    // Other page, we do not want css etc stuff so filter out
                    if (!pathElements[1].contains(".")) {
                        otherPageHit = true;
                    }

                } else if ("tags".equals(this.context)) {
                    String tagsString = pathElements[1].replace('+', ' ');
                    this.tags = Utilities.splitStringAsTags(URLUtilities
                            .decode(tagsString));
                    int maxSize = WebloggerConfig.getIntProperty(
                            "tags.queries.maxIntersectionSize", 3);
                    if (this.tags.size() > maxSize) {
                        throw new InvalidRequestException(
                                "max number of tags allowed is " + maxSize
                                        + ", " + request.getRequestURL());
                    }

                    // Other page
                    otherPageHit = true;

                } else {
                    throw new InvalidRequestException("context " + this.context
                            + "not supported, " + request.getRequestURL());
                }

            } else {
                // empty data is only allowed for the tags section
                if (!"tags".equals(this.context)) {
                    throw new InvalidRequestException("invalid index page, "
                            + request.getRequestURL());
                }
            }
        } else {
            // default page
            websitePageHit = true;
        }

        /*
         * parse request parameters
         * 
         * the only params we currently allow are: date - specifies a weblog
         * date string cat - specifies a weblog category anchor - specifies a
         * weblog entry (old way) entry - specifies a weblog entry
         * 
         * we only allow request params if the path info is null or on user
         * defined pages (for backwards compatability). this way we prevent
         * mixing of path based and query param style urls.
         */
        if (pathInfo == null || this.weblogPageName != null) {

            // check for entry/anchor params which indicate permalink
            if (request.getParameter("entry") != null) {
                String anchor = request.getParameter("entry");
                if (StringUtils.isNotEmpty(anchor)) {
                    this.weblogAnchor = anchor;
                }
            } else if (request.getParameter("anchor") != null) {
                String anchor = request.getParameter("anchor");
                if (StringUtils.isNotEmpty(anchor)) {
                    this.weblogAnchor = anchor;
                }
            }

            // only check for other params if we didn't find an anchor above or
            // tags
            if (this.weblogAnchor == null && this.tags == null) {
                if (request.getParameter("date") != null) {
                    String date = request.getParameter("date");
                    if (this.isValidDateString(date)) {
                        this.weblogDate = date;
                    } else {
                        throw new InvalidRequestException("invalid date, "
                                + request.getRequestURL());
                    }
                }

                if (request.getParameter("cat") != null) {
                    this.weblogCategoryName = URLUtilities.decode(request
                            .getParameter("cat"));
                }
            }
        }

        // page request param is supported in all views
        if (request.getParameter("page") != null) {
            String pageInt = request.getParameter("page");
            try {
                this.pageNum = Integer.parseInt(pageInt);
            } catch (NumberFormatException e) {
                // ignored, bad input
            }
        }

        // build customParams Map, we remove built-in params because we only
        // want this map to represent params defined by the template author
        customParams = new HashMap<>(request.getParameterMap());
        customParams.remove("entry");
        customParams.remove("anchor");
        customParams.remove("date");
        customParams.remove("cat");
        customParams.remove("page");
        customParams.remove("tags");

        if (log.isDebugEnabled()) {
            log.debug("context = " + stripAll(this.context));
            log.debug("weblogAnchor = " + stripAll(this.weblogAnchor));
            log.debug("weblogDate = " + stripAll(this.weblogDate));
            log.debug("weblogCategory = " + stripAll(this.weblogCategoryName));
            log.debug("tags = " + this.tags);
            log.debug("weblogPage = " + stripAll(this.weblogPageName));
            log.debug("pageNum = " + this.pageNum);
        }
    }

    boolean isValidDestination(String servlet) {
        return (servlet != null && PAGE_SERVLET.equals(servlet));
    }

    private boolean isValidDateString(String dateString) {
        // string must be all numeric and 6 or 8 characters
        return (dateString != null && StringUtils.isNumeric(dateString) && (dateString
                .length() == 6 || dateString.length() == 8));
    }

    public String getContext() {
        return context;
    }

    public void setContext(String context) {
        this.context = context;
    }

    public String getWeblogAnchor() {
        return weblogAnchor;
    }

    public void setWeblogAnchor(String weblogAnchor) {
        this.weblogAnchor = weblogAnchor;
    }

    public String getWeblogPageName() {
        return weblogPageName;
    }

    public void setWeblogPageName(String weblogPage) {
        this.weblogPageName = weblogPage;
    }

    public String getWeblogCategoryName() {
        return weblogCategoryName;
    }

    public void setWeblogCategoryName(String weblogCategory) {
        this.weblogCategoryName = weblogCategory;
    }

    public String getWeblogDate() {
        return weblogDate;
    }

    public void setWeblogDate(String weblogDate) {
        this.weblogDate = weblogDate;
    }

    public int getPageNum() {
        return pageNum;
    }

    public void setPageNum(int pageNum) {
        this.pageNum = pageNum;
    }

    public Map<String, String[]> getCustomParams() {
        return customParams;
    }

    public void setCustomParams(Map<String, String[]> customParams) {
        this.customParams = customParams;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public WeblogEntry getWeblogEntry() {

        if (weblogEntry == null && weblogAnchor != null) {
            try {
                WeblogEntryManager wmgr = WebloggerFactory.getWeblogger()
                        .getWeblogEntryManager();
                weblogEntry = wmgr.getWeblogEntryByAnchor(getWeblog(),
                        weblogAnchor);
            } catch (WebloggerException ex) {
                log.error("Error getting weblog entry " + weblogAnchor, ex);
            }
        }

        return weblogEntry;
    }

    public void setWeblogEntry(WeblogEntry weblogEntry) {
        this.weblogEntry = weblogEntry;
    }

    public ThemeTemplate getWeblogPage() {

        if (weblogPage == null && weblogPageName != null) {
            try {
                weblogPage = getWeblog().getTheme().getTemplateByLink(
                        weblogPageName);
            } catch (WebloggerException ex) {
                log.error("Error getting weblog page " + weblogPageName, ex);
            }
        }

        return weblogPage;
    }

    public void setWeblogPage(WeblogTemplate weblogPage) {
        this.weblogPage = weblogPage;
    }

    public WeblogCategory getWeblogCategory() {

        if (weblogCategory == null && weblogCategoryName != null) {
            try {
                WeblogEntryManager wmgr = WebloggerFactory.getWeblogger()
                        .getWeblogEntryManager();
                weblogCategory = wmgr.getWeblogCategoryByName(getWeblog(),
                        weblogCategoryName);
            } catch (WebloggerException ex) {
                log.error(
                        "Error getting weblog category " + weblogCategoryName,
                        ex);
            }
        }

        return weblogCategory;
    }

    public void setWeblogCategory(WeblogCategory weblogCategory) {
        this.weblogCategory = weblogCategory;
    }

    /**
     * Checks if is website page hit.
     * 
     * @return true, if is website page hit
     */
    public boolean isWebsitePageHit() {
        return websitePageHit;
    }

    /**
     * Sets the website page hit.
     * 
     * @param websitePageHit
     *            the new website page hit
     */
    public void setWebsitePageHit(boolean websitePageHit) {
        this.websitePageHit = websitePageHit;
    }

    /**
     * Checks if is other page hit.
     * 
     * @return true, if is other page hit
     */
    public boolean isOtherPageHit() {
        return otherPageHit;
    }

    /**
     * Sets the other page hit.
     * 
     * @param otherPageHit
     *            the new other page hit
     */
    public void setOtherPageHit(boolean otherPageHit) {
        this.otherPageHit = otherPageHit;
    }

}
