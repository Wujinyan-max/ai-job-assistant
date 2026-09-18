package com.jobassistant.ai;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * 面试题库的固定分类。
 * <p>分类名如果完全交给模型自由发挥，同一个技术方向会被拆成好几个桶
 * （「项目」和「项目深挖」、「MySQL」「Redis」和「数据库与中间件」），
 * 题库的筛选下拉框会越来越乱。这里把分类收敛成一套固定值：
 * 出题时只允许从 {@link #labels()} 里选，落库前再用 {@link #normalize(String)} 兜底，
 * 保证同一个技术方向永远落在同一个分类下。</p>
 */
public enum QuestionCategory {

    /** 语言与编程基础：Java 集合、并发、JVM、语言语法等 */
    LANGUAGE("编程语言与基础",
            "基础", "语言", "语法", "面向对象", "集合", "线程", "多线程", "并发编程",
            "java", "python", "golang", "c++", "c#", "php", "javascript", "typescript", "scala", "kotlin",
            "jvm", "gc"),
    /** 框架与中间件：Spring 全家桶、ORM、消息队列、微服务、容器等 */
    FRAMEWORK("框架与中间件",
            "框架", "中间件", "消息队列", "微服务", "spring", "mybatis", "hibernate", "dubbo", "netty",
            "kafka", "rabbitmq", "rocketmq", "mq", "tomcat", "nginx", "docker", "k8s", "kubernetes",
            "vue", "react", "前端", "node"),
    /** 数据库与缓存：MySQL、Redis、索引与慢查询等 */
    DATABASE("数据库与缓存",
            "数据库", "缓存", "索引", "慢查询", "分库分表", "sql", "mysql", "redis", "mongo", "oracle",
            "postgres", "mariadb", "elasticsearch"),
    /** 系统设计与性能：架构设计、高并发、容量与调优 */
    DESIGN("系统设计与性能",
            "高并发", "高可用", "架构", "系统设计", "设计", "分布式", "性能", "扩展", "限流", "降级", "容灾", "容量"),
    /** 测试与质量：测试用例设计、自动化测试、接口与性能测试等 */
    TESTING("测试与质量",
            "测试", "用例", "质量", "缺陷", "bug", "qa"),
    /** 项目与业务：简历项目深挖、业务理解 */
    PROJECT("项目与业务",
            "项目", "业务", "简历", "工作经历", "实习经历"),
    /** HR 与软素质：稳定性、沟通协作、职业规划 */
    HR("HR与软素质",
            "hr", "软素质", "沟通", "职业规划", "离职", "加班", "抗压", "团队协作", "自我介绍", "人力资源"),
    /** 兜底分类：模型给不出有效分类时落到这里，不参与出题选择 */
    OTHER("其他");

    /**
     * 归一化时的匹配优先级。
     * <p>关键词越具体的越靠前，例如「接口与性能测试」要落在测试而不是系统设计，
     * 「数据库与中间件」要落在数据库而不是框架。</p>
     */
    private static final List<QuestionCategory> PRIORITY =
            List.of(HR, PROJECT, TESTING, DATABASE, FRAMEWORK, LANGUAGE, DESIGN);

    private static final List<String> ALL_LABELS =
            Arrays.stream(values()).map(QuestionCategory::label).toList();

    private final String label;
    private final List<String> keywords;

    QuestionCategory(String label, String... keywords) {
        this.label = label;
        this.keywords = List.of(keywords);
    }

    public String label() {
        return label;
    }

    /** 出题时可选的全部分类，顺序即前端下拉框的展示顺序 */
    public static List<String> labels() {
        return ALL_LABELS.stream().filter(label -> !OTHER.label.equals(label)).toList();
    }

    /** 全部分类（含兜底分类） */
    public static List<String> allLabels() {
        return ALL_LABELS;
    }

    /** 把模型返回的自由分类名归一化成固定分类；识别不出来时落到「其他」 */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return OTHER.label;
        }
        String text = raw.trim().toLowerCase();
        for (QuestionCategory category : PRIORITY) {
            for (String keyword : category.keywords) {
                if (text.contains(keyword)) {
                    return category.label;
                }
            }
        }
        return OTHER.label;
    }

    /** 分类展示顺序的比较器，不在固定分类里的排在最后 */
    public static Comparator<String> canonicalOrder() {
        return Comparator.comparingInt(label -> {
            int index = ALL_LABELS.indexOf(label);
            return index < 0 ? ALL_LABELS.size() : index;
        });
    }
}
