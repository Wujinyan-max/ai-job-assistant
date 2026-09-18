package com.jobassistant.ai;

import com.jobassistant.vo.ResumeStructureVO;
import com.jobassistant.vo.ResumeStructureVO.Basics;
import com.jobassistant.vo.ResumeStructureVO.Education;
import com.jobassistant.vo.ResumeStructureVO.Project;
import com.jobassistant.vo.ResumeStructureVO.Work;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 纯文本简历 → 结构化简历的本地规则解析器（未配置 API Key 时 MockAiEngine 的兜底实现）。
 * <p>只解决两件最影响排版的事：板块标题归属（PDF 复制出来的标题可能排在所属内容的后面）
 * 和硬换行合并（一句话被拆成两行时要拼回一条）。</p>
 */
public final class ResumeStructureParser {

    private ResumeStructureParser() {
    }

    /** 板块定义：aliases 用于识别标题，expect / reject 关键词用于给两种解析顺序打分 */
    private record Section(String key, List<String> aliases, List<String> expect, List<String> reject) {
    }

    private static final List<Section> SECTIONS = List.of(
            new Section("education",
                    List.of("教育背景", "教育经历", "教育信息", "学历背景", "学习经历", "教育"),
                    List.of("学校", "学院", "大学", "本科", "硕士", "大专", "博士", "研究生", "专业", "学历"),
                    List.of("公司", "集团", "项目", "工作职责")),
            new Section("work",
                    List.of("工作经验", "工作经历", "实习经历", "工作履历", "职业经历", "工作背景", "实习经验"),
                    List.of("公司", "有限", "集团", "科技", "银行", "实习", "任职", "工程师", "岗位", "职责"),
                    List.of("学院", "大学", "学校", "GPA", "主修课程", "在校", "奖学金")),            new Section("projects",
                    List.of("项目经验", "项目经历", "项目实践", "项目案例", "主要项目", "项目介绍"),
                    List.of("项目", "架构", "描述", "职责", "系统", "平台"),
                    List.of("公司", "学历", "奖学金", "证书")),
            new Section("skills",
                    List.of("专业技能", "技能特长", "技能清单", "技术栈", "掌握技能", "IT技能", "技能"),
                    List.of("熟悉", "掌握", "熟练", "了解"),
                    List.of("获奖", "证书", "大学", "学院")),
            new Section("honors",
                    List.of("荣誉证书", "获奖经历", "荣誉奖项", "奖项荣誉", "获奖情况", "证书奖项", "荣誉"),
                    List.of("奖", "证书", "荣誉", "大赛", "称号", "认证"),
                    List.of("熟悉", "掌握", "熟练")),
            new Section("summary",
                    List.of("自我评价", "个人简介", "自我介绍", "个人评价", "自我总结", "个人优势"),
                    List.of("我", "本人", "性格", "擅长", "沟通"),
                    List.of()));

    /** 一行文本：titleKey 非空表示这一行是板块标题 */
    private record Line(String text, String titleKey) {
    }

    /** 一类事项的时间区间：2022-09 ~ 2026-07 / 2019.09—2023.06 / 2025年9月-2026年1月 / 2024-07-至今 */
    private static final Pattern DATE_RANGE = Pattern.compile(
            "(\\d{4}\\s*[-./年]\\s*\\d{1,2}\\s*月?\\s*[-~～至到—–]\\s*(?:至今|现在|今|\\d{4}\\s*[-./年]\\s*\\d{1,2}\\s*月?))");
    /** 条目编号：1. / 2、 / - / · 之类，去掉它们才方便合并同一句话 */
    private static final Pattern BULLET_PREFIX = Pattern.compile("^\\s*(?:\\d{1,2}\\s*[.、)）]|[-*·•●▪])\\s*");
    private static final Pattern PHONE = Pattern.compile("1[3-9]\\d{9}");
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.]+");
    private static final Pattern SCHOOL = Pattern.compile("([\\u4e00-\\u9fa5]{2,15}?(?:大学|学院|学校))");
    private static final Pattern DEGREE = Pattern.compile("(博士后|博士|硕士|研究生|本科|学士|大专|专科|中专|高中)");
    private static final Pattern PAREN = Pattern.compile("[（(]([^）)]{1,12})[）)]");
    private static final Pattern WORK_YEARS = Pattern.compile("(\\d{1,2}\\s*年(?:以上)?(?:工作)?经验)");
    private static final Pattern ROLE = Pattern.compile(
            "(工程师|开发|测试|实习生|实习|运营|产品|设计|专员|经理|助理|分析师|研发|架构师|架构|运维|主管|顾问|总监|教师|会计|销售|客服)");
    private static final Pattern TITLE_NOISE = Pattern.compile("[\\s　\\[\\]【】()（）·•●▪■▮▶▸◆◇□*:：-]+");
    private static final Pattern TITLE_SUFFIX = Pattern.compile("[A-Za-z&/ ]*");
    /** 图标字体（简历模板里的板块小图标）落在私用区，PDF 抽取后就是乱码方块，先清掉 */
    private static final Pattern ICON_GLYPH = Pattern.compile(
            "[\\uE000-\\uF8FF\\x{F0000}-\\x{FFFFD}\\x{100000}-\\x{10FFFD}]");
    private static final List<String> CITIES = List.of(
            "北京", "上海", "广州", "深圳", "杭州", "成都", "武汉", "南京", "西安", "苏州",
            "长沙", "重庆", "天津", "郑州", "东莞", "佛山", "珠海", "厦门", "合肥", "济南",
            "青岛", "大连", "沈阳", "宁波", "无锡", "福州", "昆明", "南昌", "贵阳", "哈尔滨",
            "长春", "石家庄", "太原", "南宁", "兰州", "海口", "三亚", "香港", "澳门", "惠州", "中山");
    /** 基本信息行里这些词只是背景信息，不是求职意向也不是城市 */
    private static final Set<String> STATUS_WORDS = Set.of(
            "男", "女", "汉族", "团员", "党员", "群众", "预备党员", "未婚", "已婚", "应届生", "应届",
            "随时到岗", "随时可到岗", "在职", "离职", "在读", "无", "面议");
    /** 项目条目里这些前缀的行放「项目描述」而不是「工作内容」 */
    private static final Pattern SUMMARY_LABEL = Pattern.compile(
            "^(项目架构|项目描述|项目背景|项目简介|技术架构|技术栈|项目成果)");
    /** 只有标题没有内容的行直接丢掉 */
    private static final Pattern DROP_LABEL = Pattern.compile("^(工作内容|项目职责|主要职责|工作职责|项目内容)[：:]?$");
    /** 项目正文里的段落标签，同一个标签在一段里出现两次就说明换项目了 */
    private static final Pattern GROUP_LABEL = Pattern.compile(
            "^(项目架构|项目描述|项目背景|项目简介|技术架构|技术栈|项目成果|项目内容|工作内容|工作职责|项目职责|主要职责|个人职责)");
    private static final int MAX_HEADER_LINES = 5;
    // ------------------------------------------------------------ 入口

    /** 解析入口：先按板块切分，再逐板块按字段规则组装成结构化简历 */
    public static ResumeStructureVO parse(String rawText) {
        if (!StringUtils.hasText(rawText)) {
            return ResumeStructureVO.empty();
        }
        List<Line> lines = splitLines(rawText);
        if (lines.isEmpty()) {
            return ResumeStructureVO.empty();
        }
        Basics basics = extractBasics(lines);
        List<SectionBlock> blocks = splitSections(lines);
        if (blocks.isEmpty()) {
            // 一个板块标题都没有：整段文本当成自我评价，至少别让内容丢掉
            return new ResumeStructureVO(withSummary(basics, texts(lines, headerEnd(lines), lines.size())),
                    null, null, null, null, null);
        }
        return new ResumeStructureVO(
                withSummary(basics, contentOf(blocks, "summary")),
                toEducation(contentOf(blocks, "education")),
                toWork(contentOf(blocks, "work")),
                toProjects(contentOf(blocks, "projects")),
                toItems(contentOf(blocks, "skills")),
                toItems(contentOf(blocks, "honors")));
    }

    private static Basics withSummary(Basics basics, List<String> summaryLines) {
        if (summaryLines.isEmpty()) {
            return basics;
        }
        return new Basics(basics.name(), basics.label(), basics.phone(), basics.email(),
                basics.city(), basics.workYears(), joinParagraphs(summaryLines));
    }

    /** 拆行：丢掉空行与零宽字符，并把「项目经历：xxx」这种标题和正文挤在一行的写法拆开 */
    private static List<Line> splitLines(String rawText) {
        List<Line> lines = new ArrayList<>();
        String normalized = ICON_GLYPH.matcher(rawText.replace('\u00a0', ' ').replace("\u200b", "")).replaceAll(" ");
        for (String raw : normalized.split("\\R")) {
            String text = raw.trim();
            if (text.isEmpty()) {
                continue;
            }
            String key = titleKey(text);
            if (key != null) {
                lines.add(new Line(text, key));
                continue;
            }
            String[] inline = splitInlineTitle(text);
            if (inline != null) {
                lines.add(new Line(inline[0], inline[1]));
                lines.add(new Line(inline[2], null));
                continue;
            }
            lines.add(new Line(text, null));
        }
        return lines;
    }

    /** 整行就是板块标题时返回板块 key（允许「教育背景 EDUCATION」这种中英混排） */
    private static String titleKey(String text) {
        String plain = TITLE_NOISE.matcher(text).replaceAll("");
        for (Section section : SECTIONS) {
            for (String alias : section.aliases()) {
                if (plain.startsWith(alias)
                        && TITLE_SUFFIX.matcher(plain.substring(alias.length())).matches()) {
                    return section.key();
                }
            }
        }
        return null;
    }

    /** 「项目经历：1. xxx」这类同一行的标题 + 正文，拆成 [标题, 板块 key, 正文] */
    private static String[] splitInlineTitle(String text) {
        for (Section section : SECTIONS) {
            for (String alias : section.aliases()) {
                if (text.length() > alias.length() + 1 && text.startsWith(alias)) {
                    char next = text.charAt(alias.length());
                    if (next == '：' || next == ':' || next == '、') {
                        String rest = text.substring(alias.length() + 1).trim();
                        if (!rest.isEmpty()) {
                            return new String[]{alias, section.key(), rest};
                        }
                    }
                }
            }
        }
        return null;
    }
    // ------------------------------------------------------------ 板块切分

    /** 一个板块：key + 归属它的正文行 */
    private record SectionBlock(String key, List<String> content) {
    }

    /**
     * 给其他本地 AI 能力复用的板块分组结果。lines 保留原始内容行，标题本身不包含在内。
     */
    public record ClassifiedSection(String key, List<String> lines) {
        public ClassifiedSection {
            lines = List.copyOf(lines);
        }
    }

    /** 使用与简历排版相同的前置/后置标题判断，把原始文本分到统一板块。 */
    public static List<ClassifiedSection> classifySections(String rawText) {
        if (!StringUtils.hasText(rawText)) {
            return List.of();
        }
        List<Line> lines = splitLines(rawText);
        return splitSections(lines).stream()
                .map(block -> new ClassifiedSection(block.key(), block.content()))
                .toList();
    }

    /**
     * 切分板块。PDF 复制出来的简历有两种排布：标题在正文前面（正常）和标题被挤到正文后面，
     * 两种都算一遍，再用板块关键词打分挑出更像样的那种。
     */
    private static List<SectionBlock> splitSections(List<Line> lines) {
        int headEnd = headerEnd(lines);
        List<Integer> titleIndexes = new ArrayList<>();
        for (int i = headEnd; i < lines.size(); i++) {
            if (lines.get(i).titleKey() != null) {
                titleIndexes.add(i);
            }
        }
        if (titleIndexes.isEmpty()) {
            return List.of();
        }
        List<SectionBlock> titleBefore = buildBlocks(lines, headEnd, titleIndexes, false);
        List<SectionBlock> titleAfter = buildBlocks(lines, headEnd, titleIndexes, true);
        return score(titleAfter) > score(titleBefore) ? titleAfter : titleBefore;
    }

    private static List<SectionBlock> buildBlocks(List<Line> lines, int headEnd,
                                                  List<Integer> titleIndexes, boolean titleAfter) {
        List<SectionBlock> blocks = new ArrayList<>();
        for (int i = 0; i < titleIndexes.size(); i++) {
            int title = titleIndexes.get(i);
            int from;
            int to;
            if (titleAfter) {
                from = i == 0 ? headEnd : titleIndexes.get(i - 1) + 1;
                to = title;
            } else {
                from = title + 1;
                to = i + 1 < titleIndexes.size() ? titleIndexes.get(i + 1) : lines.size();
            }
            blocks.add(new SectionBlock(lines.get(title).titleKey(), texts(lines, from, to)));
        }
        return blocks;
    }

    private static List<String> texts(List<Line> lines, int from, int to) {
        List<String> result = new ArrayList<>();
        for (int i = Math.max(from, 0); i < Math.min(to, lines.size()); i++) {
            result.add(lines.get(i).text());
        }
        return result;
    }

    /** 板块内容越像这个板块该有的样子分越高；整块为空则扣分 */
    private static int score(List<SectionBlock> blocks) {
        int total = 0;
        for (SectionBlock block : blocks) {
            String text = String.join("\n", block.content());
            if (!StringUtils.hasText(text)) {
                total -= 3;
                continue;
            }
            total += 2;
            Section section = sectionOf(block.key());
            for (String keyword : section.expect()) {
                if (text.contains(keyword)) {
                    total += 2;
                }
            }
            for (String keyword : section.reject()) {
                if (text.contains(keyword)) {
                    total -= 3;
                }
            }
        }
        return total;
    }

    private static Section sectionOf(String key) {
        for (Section section : SECTIONS) {
            if (section.key().equals(key)) {
                return section;
            }
        }
        throw new IllegalArgumentException("未知板块: " + key);
    }

    /** 取某个板块的所有正文行（同名板块可能出现多次，按出现顺序拼起来） */
    private static List<String> contentOf(List<SectionBlock> blocks, String key) {
        List<String> lines = new ArrayList<>();
        for (SectionBlock block : blocks) {
            if (block.key().equals(key)) {
                lines.addAll(block.content());
            }
        }
        return lines;
    }
    // ------------------------------------------------------------ 基本信息

    /**
     * 基本信息只在第一个板块标题之前找：简历开头固定是「姓名 / 意向城市 / 联系方式」这几行，
     * 找到第一行教育或工作内容就停，免得把正文误当成联系方式。
     */
    private static int headerEnd(List<Line> lines) {
        int limit = lines.size();
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).titleKey() != null) {
                limit = i;
                break;
            }
        }
        if (limit == 0 || lines.get(0).titleKey() != null) {
            return 0;
        }
        int end = 0;
        int i = 0;
        if (looksLikeName(lines.get(0).text())) {
            end = 1;
            i = 1;
        }
        // 姓名后面那一行固定算基本信息；再往后只有带手机号 / 邮箱的行才算
        while (i < limit && i < MAX_HEADER_LINES && isBasicsLine(lines.get(i).text())) {
            i++;
            end = i;
        }
        return end;
    }

    private static Basics extractBasics(List<Line> lines) {
        int end = headerEnd(lines);
        if (end == 0) {
            return Basics.empty();
        }
        String name = looksLikeName(lines.get(0).text()) ? lines.get(0).text() : null;
        StringBuilder rest = new StringBuilder();
        for (int i = name == null ? 0 : 1; i < end; i++) {
            rest.append(' ').append(lines.get(i).text());
        }
        String header = rest.toString();
        Matcher phone = PHONE.matcher(header);
        Matcher email = EMAIL.matcher(header);
        String label = null;
        String city = null;
        for (String token : tokens(header)) {
            String tokenCity = cityOf(token);
            if (label == null && tokenCity == null && !STATUS_WORDS.contains(token)
                    && ROLE.matcher(token).find() && !WORK_YEARS.matcher(token).find()) {
                label = token;
            } else if (city == null && tokenCity != null) {
                city = tokenCity;
            }
        }
        return new Basics(name, label, phone.find() ? phone.group() : null,
                email.find() ? email.group() : null, city, workYears(header), null);
    }

    /** 工作年限：能写「5 年经验」也能写「应届生」，都没有就不猜 */
    private static String workYears(String header) {
        Matcher matcher = WORK_YEARS.matcher(header);
        if (matcher.find()) {
            return matcher.group(1).replaceAll("\\s+", "");
        }
        return header.contains("应届") ? "应届生" : null;
    }

    private static boolean looksLikeName(String text) {
        if (text.length() > 12) {
            return false;
        }
        if (PHONE.matcher(text).find() || EMAIL.matcher(text).find()) {
            return false;
        }
        return TITLE_NOISE.matcher(text).replaceAll("").matches("[\\u4e00-\\u9fa5]{2,12}");
    }

    private static boolean isBasicsLine(String text) {
        if (PHONE.matcher(text).find() || EMAIL.matcher(text).find()) {
            return true;
        }
        return !DATE_RANGE.matcher(text).find() && basicsTokenCount(text) >= 2;
    }

    private static int basicsTokenCount(String text) {
        int count = 0;
        for (String token : tokens(text)) {
            if (isBasicsToken(token)) {
                count++;
            }
        }
        return count;
    }

    private static boolean isBasicsToken(String token) {
        if (token.isEmpty()) {
            return false;
        }
        return PHONE.matcher(token).matches() || EMAIL.matcher(token).find()
                || STATUS_WORDS.contains(token) || ROLE.matcher(token).find()
                || cityOf(token) != null || WORK_YEARS.matcher(token).find()
                || token.endsWith("岁") || token.endsWith("省");
    }

    private static String[] tokens(String text) {
        return text.split("[\\s|｜·•/、,，]+");
    }

    /** 期望城市 / 现居城市：既认「广州」，也认「广州市」 */
    private static String cityOf(String token) {
        if (token.isEmpty()) {
            return null;
        }
        if (CITIES.contains(token)) {
            return token;
        }
        if (token.length() >= 3 && token.endsWith("市")) {
            return token.substring(0, token.length() - 1);
        }
        for (String city : CITIES) {
            if (token.length() > city.length() && token.startsWith(city)) {
                return city;
            }
        }
        return null;
    }
    // ------------------------------------------------------------ 逐板块抽取

    /** 按「条目头」把板块正文切成若干条：条目头带时间区间，或者带学校 / 公司名 */
    private static List<List<String>> groupEntries(List<String> content) {
        List<List<String>> entries = new ArrayList<>();
        for (String line : content) {
            if (entries.isEmpty() || isEntryHead(line)) {
                entries.add(new ArrayList<>());
            }
            entries.get(entries.size() - 1).add(line);
        }
        return entries;
    }

    private static boolean isEntryHead(String line) {
        // 带编号的一定是正文条目，不是新的一段经历
        if (BULLET_PREFIX.matcher(line).find()) {
            return false;
        }
        if (DATE_RANGE.matcher(line).find()) {
            return true;
        }
        // 段落长句里冒出「银行 / 学校 / 中心」这类词只是内容，不是新的经历标题
        if (line.length() > 40 || line.contains("。")) {
            return false;
        }
        return containsAny(line, "大学", "学院", "学校", "公司", "有限", "集团", "银行",
                "研究院", "事务所", "工作室", "中心", "科技");
    }

    /**
     * 合并硬换行：PDF 复制出来的长句常被切成两行，只有「上一句已经结束」或者
     * 「这一行带编号」时才另起一条，否则拼回上一条。
     */
    private static List<String> mergeLines(List<String> lines) {
        List<String> items = new ArrayList<>();
        for (String line : lines) {
            String text = line.trim();
            if (text.isEmpty()) {
                continue;
            }
            boolean newItem = items.isEmpty() || BULLET_PREFIX.matcher(text).find()
                    || endsSentence(items.get(items.size() - 1));
            if (newItem) {
                items.add(BULLET_PREFIX.matcher(text).replaceFirst("").trim());
            } else {
                int last = items.size() - 1;
                items.set(last, items.get(last) + text);
            }
        }
        return items;
    }

    private static boolean endsSentence(String text) {
        if (text.isEmpty()) {
            return true;
        }
        char last = text.charAt(text.length() - 1);
        return last == '。' || last == '；' || last == ';'
                || last == '！' || last == '？' || last == '.' || last == '!';
    }

    private static String joinParagraphs(List<String> lines) {
        return String.join("\n", mergeLines(lines));
    }

    private static boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
    private static List<Education> toEducation(List<String> content) {
        List<Education> result = new ArrayList<>();
        for (List<String> entry : groupEntries(content)) {
            String head = entry.get(0);
            String body = DATE_RANGE.matcher(head).replaceFirst("").trim();
            String school = firstMatch(SCHOOL, body);
            String degree = firstMatch(DEGREE, body);
            result.add(new Education(school, majorOf(body, school, degree), degree, periodOf(head),
                    entry.size() > 1 ? joinParagraphs(entry.subList(1, entry.size())) : null));
        }
        return result;
    }

    /** 专业名：从「学校 专业（学历）」里去掉学校和学历，剩下的那个词就是专业 */
    private static String majorOf(String body, String school, String degree) {
        String text = PAREN.matcher(body).replaceAll(" ");
        if (school != null) {
            text = text.replace(school, " ");
        }
        if (degree != null) {
            text = text.replace(degree, " ");
        }
        for (String token : tokens(text)) {
            if (token.matches("[\\u4e00-\\u9fa5A-Za-z]{2,20}") && cityOf(token) == null) {
                return token;
            }
        }
        return null;
    }

    private static String periodOf(String text) {
        Matcher matcher = DATE_RANGE.matcher(text);
        return matcher.find() ? matcher.group(1).replaceAll("\\s+", "") : null;
    }

    private static String firstMatch(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static List<Work> toWork(List<String> content) {
        List<Work> result = new ArrayList<>();
        for (List<String> entry : groupEntries(content)) {
            String head = entry.get(0);
            String[] names = tokens(DATE_RANGE.matcher(head).replaceFirst("").trim());
            result.add(new Work(names.length == 0 ? null : names[0], tail(names), periodOf(head),
                    entry.size() > 1 ? mergeLines(entry.subList(1, entry.size())) : List.of()));
        }
        return result;
    }

    /** 「公司 职位」里第一个词是公司，剩下的都算职位 */
    private static String tail(String[] names) {
        StringBuilder position = new StringBuilder();
        for (int i = 1; i < names.length; i++) {
            position.append(i > 1 ? " " : "").append(names[i]);
        }
        return position.isEmpty() ? null : position.toString();
    }

    /**
     * 有的模板会把几个项目标题连着排在最前面、正文统一跟在后面，光按「标题行」切会把正文全塞给最后一个项目。
     * 这里先数出开头的标题行，再按「架构 / 描述 / 工作内容」把正文切成同样数量的段落块，一一对应回去。
     */
    private static List<List<String>> alignProjectBlocks(List<String> content) {
        int heads = 0;
        while (heads < content.size() && isEntryHead(content.get(heads))) {
            heads++;
        }
        if (heads < 2 || heads == content.size()) {
            return groupEntries(content);
        }
        List<List<String>> blocks = new ArrayList<>();
        Set<String> labels = new HashSet<>();
        for (String line : content.subList(heads, content.size())) {
            Matcher matcher = GROUP_LABEL.matcher(line);
            String label = matcher.find() ? matcher.group(1) : null;
            // 同一个标签在一段里出现第二次（比如下一个项目的「项目架构」），说明换项目了
            if (blocks.isEmpty() || label != null && !labels.add(label)) {
                blocks.add(new ArrayList<>());
                labels = new HashSet<>();
                if (label != null) {
                    labels.add(label);
                }
            }
            blocks.get(blocks.size() - 1).add(line);
        }
        if (blocks.size() != heads) {
            return groupEntries(content);
        }
        List<List<String>> entries = new ArrayList<>();
        for (int i = 0; i < heads; i++) {
            List<String> entry = new ArrayList<>();
            entry.add(content.get(i));
            entry.addAll(blocks.get(i));
            entries.add(entry);
        }
        return entries;
    }

    private static List<Project> toProjects(List<String> content) {
        List<Project> result = new ArrayList<>();
        for (List<String> entry : alignProjectBlocks(content)) {
            // 首行本身也可能是带编号的描述，编号要先去干净
            String head = BULLET_PREFIX.matcher(entry.get(0)).replaceFirst("").trim();
            String period = periodOf(head);
            String body = DATE_RANGE.matcher(head).replaceFirst("").trim();
            String name = body;
            String role = null;
            List<String> detail = new ArrayList<>();
            if (period == null && body.length() > 30) {
                // 没时间又是长句的开头其实是项目描述：逗号前那截当项目名，剩下的接着当正文
                name = nameOfSentence(body);
                detail.add(name == null ? head
                        : body.substring(name.length()).replaceFirst("^[\\s，,、：:；;]+", "").trim());
            } else {
                String[] names = tokens(body);
                if (names.length > 1 && ROLE.matcher(names[names.length - 1]).find()) {
                    role = names[names.length - 1];
                    name = body.substring(0, body.lastIndexOf(role)).trim();
                }
            }
            if (entry.size() > 1) {
                detail.addAll(entry.subList(1, entry.size()));
            }
            List<String> summaryLines = new ArrayList<>();
            List<String> bullets = new ArrayList<>();
            for (String item : mergeLines(detail)) {
                if (SUMMARY_LABEL.matcher(item).find()) {
                    summaryLines.add(item);
                } else if (!DROP_LABEL.matcher(item).matches()) {
                    bullets.add(item);
                }
            }
            result.add(new Project(name, role, period,
                    summaryLines.isEmpty() ? null : String.join("\n", summaryLines), bullets));
        }
        return result;
    }

    /** 「1. 交易系统重构，负责订单模块…」这类没有标题行的项目：逗号前那截就是项目名 */
    private static String nameOfSentence(String text) {
        int cut = text.length();
        for (char separator : new char[]{'，', '：', ':', ',', '；', ';', '。'}) {
            int index = text.indexOf(separator);
            if (index > 0 && index < cut) {
                cut = index;
            }
        }
        String name = text.substring(0, cut).trim();
        return name.length() >= 2 && name.length() <= 20 ? name : null;
    }

    /** 技能 / 荣誉这类没有固定字段的板块：合并硬换行后一条一条列出来 */
    private static List<String> toItems(List<String> content) {
        List<String> items = new ArrayList<>();
        for (String item : mergeLines(content)) {
            items.addAll(splitShortList(item));
        }
        return items;
    }

    /** 一行里用「、」隔开的短技能点拆成多条，长句子保持整条 */
    private static List<String> splitShortList(String text) {
        if (text.contains("。")) {
            return List.of(text);
        }
        String[] pieces = text.split("[、,，;；/]+");
        if (pieces.length < 2) {
            return List.of(text);
        }
        List<String> result = new ArrayList<>();
        for (String piece : pieces) {
            String trimmed = piece.trim();
            if (trimmed.isEmpty() || trimmed.length() > 12) {
                return List.of(text);
            }
            result.add(trimmed);
        }
        return result;
    }
}
