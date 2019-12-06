package cn.itcast.job.task;

import cn.itcast.job.pojo.JobInfo;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import us.codecraft.webmagic.Page;
import us.codecraft.webmagic.Site;
import us.codecraft.webmagic.Spider;
import us.codecraft.webmagic.processor.PageProcessor;
import us.codecraft.webmagic.scheduler.BloomFilterDuplicateRemover;
import us.codecraft.webmagic.scheduler.QueueScheduler;
import us.codecraft.webmagic.selector.Html;
import us.codecraft.webmagic.selector.Selectable;

import java.util.List;

@Component
public class JobProcessor implements PageProcessor {

    //    private String url = "https://search.51job.com/list/000000,000000,0000,32%252C01,9,99,java,2," +
//            "1.html?lang=c&stype=&postchannel=0000&workyear=99&cotype=99&degreefrom=99&jobterm=99&companysize=99" +
//            "&providesalary=99&lonlat=0%2C0&radius=-1&ord_field=0&confirmdate=9&fromType=&dibiaoid=0&address=&line" +
//            "=&specialarea=00&from=&welfare=";
    private String url = "https://search.51job.com/list/090200,000000,0000,32,9,99,%25E8%25BD%25AF%25E4%25BB%25B6%25E5%25B7%25A5%25E7%25A8%258B%25E5%25B8%2588,2," +
            "1.html?lang=c&stype=&postchannel=0000&workyear=99&cotype=99&degreefrom=99&jobterm=99&companysize=99" +
            "&providesalary=99&lonlat=0%2C0&radius=-1&ord_field=0&confirmdate=9&fromType=&dibiaoid=0&address=&line=&specialarea=00&from=&welfare=";


    @Autowired
    private SpringDataPipeline springDataPipeline;

    //initialDelay当任务启动后，等等多久执行方法
    //fixedDelay每个多久执行方法
    @Scheduled(initialDelay = 1000, fixedDelay = 100 * 1000)
    public void process() {
        Spider.create(new JobProcessor())
                .addUrl(url)
                .setScheduler(new QueueScheduler().setDuplicateRemover(new BloomFilterDuplicateRemover(100000)))
                .thread(10)
                .addPipeline(this.springDataPipeline)
                .run();
    }

    @Override
    public void process(Page page) {
        //解析页面，获取招聘信息详情的url地址
        String html2 = page.getHtml().toString();
        List<Selectable> list = page.getHtml().css("div#resultList div.el").nodes();
        System.out.println("查看page获取到的招聘列表是否为空，为0则代表此页是详情页！：[ "+list.size());

        //判断获取到的集合是否为空
        if (list.size() == 0) {
            // 如果为空，表示这是招聘详情页,解析页面，获取招聘详情信息，保存数据
            System.out.println("+++++++2. 说明当前page是招聘详情页,解析页面，获取招聘详情信息，保存数据--- start parse detaill +++++++++++++++++");
            this.saveJobInfo(page);

        } else {
            System.out.println("+++++++1.说明当前page是列表页--- start parse url =+++++++++++++++++++++++++++++++++++++++++++++++++++++++");
            //如果不为空，表示这是列表页,解析出详情页的url地址，放到任务队列中
            for (Selectable selectable : list) {
                //获取url地址
                String jobInfoUrl = selectable.links().toString();
                System.out.println("url: ["+jobInfoUrl+" ]");
                //把获取到的url地址放到任务队列中
                page.addTargetRequest(jobInfoUrl);
            }

            //获取下一页的url
            String bkUrl = page.getHtml().css("div.p_in li.bk").nodes().get(1).links().toString();
            System.out.println("+++ 下一页url:[ "+bkUrl);
            //把url放到任务队列中
            page.addTargetRequest(bkUrl);

        }


        String html = page.getHtml().toString();


    }

//    解析页面，获取招聘详情信息，保存数据
    private void saveJobInfo(Page page) {
        //创建招聘详情对象
        JobInfo jobInfo = new JobInfo();

        //解析页面
        Html html = page.getHtml();
        System.out.println("pppp+++++++++++");
        //获取数据，封装到对象中
        jobInfo.setCompanyName(html.css("div.cn p.cname a", "text").toString());
        jobInfo.setCompanyAddr(Jsoup.parse(html.css("div.bmsg").nodes().get(1).toString()).text());//公司地址
        jobInfo.setCompanyInfo(Jsoup.parse(html.css("div.tmsg").toString()).text());
        jobInfo.setJobName(html.css("div.cn h1", "text").toString());
        jobInfo.setJobAddr(Jsoup.parse(html.css("div.bmsg").nodes().get(1).toString()).text());//工作地址
//        jobInfo.setJobAddr(html.css("div.cn span.lname", "text").toString());

        jobInfo.setJobInfo(Jsoup.parse(html.css("div.job_msg").toString()).text());
        jobInfo.setUrl(page.getUrl().toString());

        //获取薪资
        Integer[] salary = MathSalary.getSalary(html.css("div.cn strong", "text").toString());
        jobInfo.setSalaryMin(salary[0]);
        jobInfo.setSalaryMax(salary[1]);

        //获取发布时间
        String timeStr = Jsoup.parse(html.css("div.cn p").nodes().get(1).toString()).text();
        int end = timeStr.lastIndexOf("发布");
        int start =end-4;
        String time = timeStr.substring(end - 5, end+2);
//        String time = Jsoup.parse(html.css("div.t1 span").regex(".*发布").toString()).text();
        jobInfo.setTime(time);


        //把结果保存起来--SpringDataPipeline会去取出，存入数据库
        System.out.println("++++3. put jobInfo data to resultItems +++++++");
        page.putField("jobInfo", jobInfo);
    }


    private Site site = Site.me()
            .setCharset("gbk")//设置编码
            .setTimeOut(10 * 1000)//设置超时时间
            .setRetrySleepTime(3000)//设置重试的间隔时间
            .setRetryTimes(3);//设置重试的次数

    @Override
    public Site getSite() {
        return site;
    }


}
