package com.myblog.controller;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.myblog.common.Config;
import com.myblog.common.SSOCommon;
import com.myblog.lucene.BlogIndex;
import com.myblog.model.*;
import com.myblog.service.*;
import com.myblog.util.*;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

import javax.annotation.Resource;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Created by Zephery on 2016/8/5.
 */

@Controller
public class IndexController {
    //logger
    private static final Logger logger = LoggerFactory.getLogger(IndexController.class);
    @Resource
    private IBlogService blogService;
    @Resource
    private ICategoryService categoryService;
    @Resource
    private ILinksService linksService;
    @Resource
    private IMyReadingService myReadingService;
    @Resource
    private ITagService tagService;
    @Resource
    private IAsyncService asyncService;
    @Resource
    private IWeiboService weiboService;
    @Resource
    private RedisTemplate redisTemplate;
    @Resource
    private MongoTemplate mongoTemplate;
    private BlogIndex blogIndex = new BlogIndex();

    /**
     * 首页
     *
     * @param request
     * @return
     * @throws Exception
     */
    @RequestMapping("/index")
    public ModelAndView index(HttpServletRequest request) throws Exception {
        String page = request.getParameter("pagenum");
        Integer pagenum;
        if (StringUtils.isEmpty(page)) {
            pagenum = 1;
        } else {
            pagenum = Integer.parseInt(page);
        }
        PageHelper.startPage(pagenum, 15);
        ModelAndView mav = new ModelAndView();
        List<Blog> lists = blogService.getAllBlog();
        List<Blog> banners = blogService.getBanner();
        PageInfo<Blog> blogs = new PageInfo<>(lists);
        Integer startpage, endpage;
        if (blogs.getPages() < 6) {
            startpage = 1;
            endpage = blogs.getPages();
        } else {
            if (pagenum > 3) {
                startpage = blogs.getPageNum() - 3;
                endpage = blogs.getPageNum() + 3 > blogs.getPages() ? blogs.getPages() : blogs.getPageNum() + 3;
            } else {
                startpage = 1;
                endpage = blogs.getPageNum() + 4;
            }
        }
        List<Blog> hotblogs = blogService.getByHits();
        mav.addObject("startpage", startpage);
        mav.addObject("endpage", endpage);
        mav.addObject("hotblogs", hotblogs);
        mav.addObject("blogs", blogs.getList());
        mav.addObject("totalpages", blogs.getPages());
        mav.addObject("pageNum", blogs.getPageNum());
        mav.addObject("banners", banners);
        mav.setViewName("index");
        return mav;
    }

    @RequestMapping("/blogbyhits")
    @ResponseBody
    public void blogbyhits(HttpServletResponse response) throws Exception {
        try {
            List<Blog> blogbyhits = blogService.getByHits();
            Gson gson = new Gson();
            String temp = gson.toJson(blogbyhits);
            response.getWriter().write(temp);
        } catch (Exception e) {
            response.getWriter().write(e.toString());
        }
    }

    @RequestMapping("/getjsonbycategories")
    @ResponseBody
    public void getbycategoryid(HttpServletResponse response) throws Exception {
        try {
            List<Category> categories = categoryService.getAllCatWithoutLife();
            Gson gson = new Gson();
            String temp = gson.toJson(categories);
            response.getWriter().write(temp);
        } catch (Exception e) {
            response.getWriter().write(e.toString());
        }
    }

    @RequestMapping("/biaoqianyun")
    @ResponseBody
    public void biaoqianyun(HttpServletResponse response) throws Exception {
        try {
            JedisUtil jedis = JedisUtil.getInstance();
            JsonParser parser = new JsonParser();
            String str = jedis.get("biaoqian");
            JsonArray jsonArray = (JsonArray) parser.parse(str);
            Iterator iterator = jsonArray.iterator();
            List<KeyAndValue> biaoqian = new ArrayList<>();
            while (iterator.hasNext()) {
                Gson gson = new Gson();
                KeyAndValue keyAndValue = gson.fromJson((JsonObject) iterator.next(), KeyAndValue.class);
                biaoqian.add(keyAndValue);
            }
            biaoqian.sort((o1, o2) -> {
                Integer a = StringUtil.stringgetint(o1.getValue());
                Integer b = StringUtil.stringgetint(o2.getValue());
                return b.compareTo(a);
            });
            Gson gson = new Gson();
            String temp = gson.toJson(biaoqian.size() > 16 ? biaoqian.subList(0, 16) : biaoqian);
            response.getWriter().write(temp);
        } catch (Exception e) {
            response.getWriter().write(e.toString());
        }
    }

    @RequestMapping("/links")
    @ResponseBody
    public void links(HttpServletResponse response) {
        try {
            List<Links> list = linksService.getAllLinks();
            Gson gson = new Gson();
            response.getWriter().write(gson.toJson(list));
        } catch (IOException e) {
            logger.error("友情链接出错", e);
        }
    }

    @RequestMapping("/myreading")
    public void myreading(HttpServletResponse response) {
        try {
            Set<Myreading> set = myReadingService.getAllReading();
            Gson gson = new Gson();
            response.getWriter().write(gson.toJson(set));
        } catch (IOException e) {
            logger.error("我的阅读出错", e);
        }
    }


    @RequestMapping(value = "/lucene")
    public void lucene(HttpServletResponse response) throws Exception {
        List<Blog> blogs = blogService.getAllBlogWithContent();
        blogIndex.refreshlucene(blogs);
        response.getWriter().write("success");
    }

    @RequestMapping(value = "/aboutme")
    public ModelAndView abountme() {
        ModelAndView mv = new ModelAndView();
        mv.setViewName("aboutme");
        return mv;
    }

    @RequestMapping(value = "/donate")
    public ModelAndView donate() {
        ModelAndView mv = new ModelAndView();
        mv.setViewName("donate");
        return mv;
    }

    @RequestMapping(value = "/404")
    public ModelAndView fourzerofour() {
        ModelAndView mv = new ModelAndView();
        mv.setViewName("404");
        return mv;
    }

    @RequestMapping("/checkcookie")
    public ModelAndView checkcookie(HttpServletRequest request) {
        ModelAndView mv = new ModelAndView("checkcookie");
        HttpSession session = request.getSession();
        Cookie[] cookies = request.getCookies();
        mv.addObject("cookies", cookies);
        mv.addObject("session", session);
        mv.addObject("request", request);
        return mv;
    }

    /**
     * @param response
     * @return
     */
    @RequestMapping("/updatetag")
    @ResponseBody
    public void updatetag(HttpServletResponse response) throws IOException {
        if (tagService.updatetag(1) == 1) {
            response.getWriter().write("update success");
        } else {
            response.getWriter().write("update error");
        }
    }


    @RequestMapping("/singleToMany")
    @ResponseBody
    public String singleToMany() throws Exception {
        SingleToMany.getInstance().test();
        return DateTime.now().toString("yyyy-MM-dd HH:mm:ss");
    }

//    /**
//     * 重启本项目
//     */
//    @RequestMapping("/restart")
//    public void restart() {
//        BashUtil.getInstance().executeRestartProject();
//    }

    /**
     * 每天更新借书记录（由于隐私关系已停掉），刷新一遍Lucene索引记录
     * 由于每次更新都要删除本地索引记录，所以时间必须在项目启动完之后再进行更新
     */
    @Scheduled(cron = "0 30 6 * * * ")
    @RequestMapping("/update")
    public void updateLuceneEverydate() throws Exception {
        List<Blog> blogs = blogService.getAllBlogWithContent();
        blogIndex.refreshlucene(blogs);//刷新博客
        logger.info("刷新博客完成");
        blogService.ajaxbuild();//刷新自动补全
        logger.info("刷新自动补全完成");
        asyncService.start();//广州图书馆借书记录
        logger.info("刷新广图借书记录完成");
        HttpHelper.getInstance().get(Config.getProperty("360"));
        logger.info("360SEO完成");
        HttpHelper.getInstance().get(Config.getProperty("baidu"));
        logger.info("baidu完成");
        List<Weibo> weibos = weiboService.getAllWeiboToday();
        if (weibos == null || weibos.size() > 0) {
            logger.info("微博已经更新过了");
        } else {
            PythonUtil.executeMyWeiBo();
            logger.info("微博更新完成");
        }
        PythonUtil.executeGetBaidu();
        logger.info("百度统计更新完成");
        tagService.updatetag(1);
        logger.info("标签云更新完成");
//        logger.info("restart:项目开始重启");
//        BashUtil.getInstance().executeRestartProject();
    }

    @RequestMapping("/pythontest")
    @ResponseBody
    public String pythontest() {
        PythonUtil.executeMyWeiBo();
        return "aa";
    }

    /**
     * 处理从QQ到12345网站的单点登录
     *
     * @param response
     * @return
     * @throws Exception
     */
    @RequestMapping("/qqLogin")
    public void login(HttpServletResponse response) throws Exception {
        try {
            String redirect_url = "https://graph.qq.com/oauth2.0/authorize?" +
                    "client_id=" + SSOCommon.qqAppKey +
                    "&redirect_uri=" + SSOCommon.qqRedirectUri +
                    "&response_type=code" +
                    "&state=" + RandomStringUtils.randomAlphanumeric(10) +      //设置为简单的随机数
                    "&scope=get_user_info";
            response.sendRedirect(redirect_url);
        } catch (Exception e) {
            logger.error("调用QQ接口异常！", e);
        }
    }

    @RequestMapping("/qqCallback")
    @ResponseBody
    @SuppressWarnings("unchecked")
    public String qqCallback(HttpServletRequest request, HttpServletResponse response) throws Exception {
        String code = request.getParameter("code");
        String toGetToken = "https://graph.qq.com/oauth2.0/token?" +
                "code=" + code +
                "&grant_type=authorization_code" +
                "&client_id=" + SSOCommon.qqAppKey +
                "&client_secret=" + SSOCommon.qqAppSecret +
                "&redirect_uri=" + SSOCommon.qqRedirectUri;
        logger.info(toGetToken);
        //access token
        String tokeContent = HttpHelper.getInstance().get(toGetToken);
        logger.info(tokeContent);
        String token = tokeContent.split("&")[0].split("=")[1];
        if (redisTemplate.opsForValue().get("savedToken") == null) {
            redisTemplate.opsForValue().set("savedToken", token);
        } else {
            String savedToken = redisTemplate.opsForValue().get("savedToken").toString();
            savedToken += "\n" + token;
            redisTemplate.opsForValue().set("savedToken", savedToken);
        }
        //openid
        //callback( {"client_id":"YOUR_APPID","openid":"YOUR_OPENID"} ); 搜索
        String openUrl = "https://graph.qq.com/oauth2.0/me?access_token=" + token;
        String openContent = HttpHelper.getInstance().get(openUrl);
        String json = openContent.replaceAll("callback\\( ", "").replace(" );", "");
        logger.info(json);
        JsonParser parser = new JsonParser();
        JsonObject object = parser.parse(json).getAsJsonObject();
        String openid = object.get("openid").toString().replaceAll("\"", "");
        //userInfo
        String url = "https://graph.qq.com/user/get_user_info?" +
                "access_token=" + token +
                "&oauth_consumer_key=101323012" +
                "&openid=" + openid +
                "&format=json";
        logger.info(url);
        String content = HttpHelper.getInstance().get(url);
        logger.info("qqlogin message");
        logger.info(content);
        logger.info("qqlogin end");
        redisTemplate.opsForList().leftPush("qqmessage", parser.parse(content).toString());
        String sss = parser.parse(content).toString();
        mongoTemplate.insert(sss, "qqMessage");
        return parser.parse(content).toString();
    }

    /**
     * 跳转到微信登陆页面
     *
     * @param request
     * @param response
     * @throws Exception
     */
    @RequestMapping("/weixinLogin")
    public void weixinLogin(HttpServletRequest request, HttpServletResponse response) throws Exception {
        try {
            String redirect_url = "https://open.weixin.qq.com/connect/qrconnect?" +
                    "appid=" + SSOCommon.weixinAppKey +
                    "&redirect_uri=" + URLEncoder.encode(SSOCommon.weixinRedirectUri, "utf-8") +
                    "&response_type=code" +
                    "&scope=" + SSOCommon.weixinScope +
                    "&state=" + RandomStringUtils.randomAlphanumeric(10) +      //设置为简单的随机数
                    "#wechat_redirect";
            logger.info(redirect_url);
            response.sendRedirect(redirect_url);
        } catch (Exception e) {
            logger.error("调用QQ接口异常！", e);
        }
    }

    /**
     * 微信回调
     *
     * @param request
     * @param response
     * @return
     * @throws Exception
     */
    @RequestMapping("/weixinReturn")
    @ResponseBody
    @SuppressWarnings("unchecked")
    public String wechatReturn(HttpServletRequest request, HttpServletResponse response) throws Exception {
        String code = request.getParameter("code");               // 注意失效
        JsonObject object = SSOUtil.getWeiXinMessage(code);
//        JsonObject object = parser.parse(mecontent).getAsJsonObject();
        String access_token = object.get("access_token").getAsString();
        String refresh_token = object.get("refresh_token").getAsString();
        String openid = object.get("openid").getAsString();
//        SSOUtil.refreshWeixinToken(refresh_token);            //刷新，可不要
        String userInfoURL = "https://api.weixin.qq.com/sns/userinfo?" +
                "access_token=" + access_token +
                "&openid=" + openid;
        return HttpHelper.getInstance().get(userInfoURL);
    }

    @RequestMapping(value = "/baidu/word", method = RequestMethod.GET)
    @ResponseBody
    public String baiduword(String url) {
        try {
            return WordRecognition.getInstance().recognizeImagePath(url);
        } catch (Exception e) {
            return null;
        }
    }

    @RequestMapping("/zhoubao")
    @SuppressWarnings("unchecked")
    public ModelAndView zhoubao() {
        ModelAndView mv = new ModelAndView();
        if (redisTemplate.opsForValue().get("zhoubao") != null) {
            mv.addObject("content", redisTemplate.opsForValue().get("zhoubao").toString());
        }
        mv.setViewName("/zhoubao");
        return mv;
    }

    @RequestMapping("/savezhoubao")
    @SuppressWarnings("unchecked")
    public void savezhoubao(String content) {
        if (StringUtils.isNotEmpty(content)) {
            content = StringUtils.trimToNull(content);
        }
        redisTemplate.opsForValue().set("zhoubao", content);
    }
}