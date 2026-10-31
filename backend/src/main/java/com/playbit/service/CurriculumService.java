package com.playbit.service;

import java.util.*;
import org.springframework.stereotype.Service;
import com.playbit.exception.ApiException;
import com.playbit.repository.Db;

@Service
public class CurriculumService {
    public record Topic(String id, String name) {}
    public record Chapter(String id, String name, List<Topic> topics) {}
    public record Catalog(List<Integer> classLevels, List<Chapter> chapters) {}

    private record ChapterSpec(String id, String name, List<String> topicIds) {}
    private static ChapterSpec chapter(int grade, String id, String name, String... topicIds) {
        return new ChapterSpec("c" + grade + "-" + id, name, List.of(topicIds));
    }
    private static List<ChapterSpec> chapters(int grade, String... triples) {
        var result=new ArrayList<ChapterSpec>();
        for(int i=0;i+2<triples.length;i+=3) result.add(chapter(grade,triples[i],triples[i+1],triples[i+2]));
        return List.copyOf(result);
    }

    private static final List<Integer> CLASS_LEVELS = java.util.stream.IntStream.rangeClosed(1, 12).boxed().toList();
    private static final Map<Integer, List<ChapterSpec>> MATH_CHAPTERS = Map.ofEntries(
        Map.entry(1, List.of(
            chapter(1,"numbers-1-20","Numbers 1 to 20","large-numbers"),
            chapter(1,"addition-subtraction","Addition and Subtraction","arithmetic"),
            chapter(1,"shapes-space","Shapes and Space","geometry"),
            chapter(1,"measurement","Measurement","area-perimeter"),
            chapter(1,"patterns","Patterns","number-patterns")
        )),
        Map.entry(2, List.of(
            chapter(2,"numbers-100","Numbers to 100","large-numbers"),
            chapter(2,"addition-subtraction","Addition and Subtraction","arithmetic"),
            chapter(2,"multiplication","Multiplication","arithmetic"),
            chapter(2,"shapes","Shapes and Figures","geometry"),
            chapter(2,"measurement","Measurement","area-perimeter"),
            chapter(2,"patterns","Patterns","number-patterns")
        )),
        Map.entry(3, List.of(
            chapter(3,"numbers-1000","Numbers to 1000","large-numbers"),
            chapter(3,"operations","Addition, Subtraction and Multiplication","arithmetic"),
            chapter(3,"division","Division","arithmetic"),
            chapter(3,"fractions","Fractions","fractions"),
            chapter(3,"shapes","Shapes and Designs","geometry"),
            chapter(3,"measurement","Measurement","area-perimeter"),
            chapter(3,"data","Data Handling","statistics"),
            chapter(3,"patterns","Patterns","number-patterns")
        )),
        Map.entry(4, List.of(
            chapter(4,"large-numbers","Large Numbers","large-numbers"),
            chapter(4,"four-operations","Four Operations","arithmetic"),
            chapter(4,"multiples-factors","Multiples and Factors","arithmetic"),
            chapter(4,"fractions","Fractions","fractions"),
            chapter(4,"geometry","Shapes, Angles and Symmetry","geometry","angles","symmetry"),
            chapter(4,"measurement","Measurement and Perimeter","area-perimeter"),
            chapter(4,"data","Data Handling","statistics"),
            chapter(4,"patterns","Patterns","number-patterns")
        )),
        Map.entry(5, List.of(
            chapter(5,"large-numbers","Large Numbers","large-numbers"),
            chapter(5,"operations","Operations with Whole Numbers","arithmetic"),
            chapter(5,"fractions-decimals","Fractions and Decimals","fractions","decimals"),
            chapter(5,"factors-multiples","Factors and Multiples","arithmetic"),
            chapter(5,"geometry","Geometry and Maps","geometry","coordinate-geometry"),
            chapter(5,"area-perimeter","Area and Perimeter","area-perimeter"),
            chapter(5,"measurement","Measurement","area-perimeter"),
            chapter(5,"data","Data Handling","statistics"),
            chapter(5,"patterns","Patterns","number-patterns")
        )),
        Map.entry(6, List.of(
            chapter(6,"large-numbers","Large Numbers Around Us","large-numbers"),
            chapter(6,"knowing-numbers","Knowing Our Numbers","large-numbers"),
            chapter(6,"whole-numbers","Whole Numbers","whole-numbers"),
            chapter(6,"arithmetic-expressions","Arithmetic Expressions","arithmetic"),
            chapter(6,"decimals","A Peek Beyond the Point","decimals"),
            chapter(6,"fractions-decimals","Fractions and Decimals","fractions","decimals"),
            chapter(6,"letter-numbers","Expressions Using Letter-Numbers","algebra"),
            chapter(6,"intro-algebra","Introduction to Algebra","algebra"),
            chapter(6,"number-play","Number Play","number-patterns"),
            chapter(6,"playing-with-numbers","Playing with Numbers","number-patterns"),
            chapter(6,"integers","Integers","integers"),
            chapter(6,"lines-angles","Parallel and Intersecting Lines","lines-angles"),
            chapter(6,"three-lines","A Tale of Three Intersecting Lines","angles"),
            chapter(6,"geometry-basics","Basic Geometrical Ideas","geometry"),
            chapter(6,"measure-angles","Measures of Lines and Angles","lines-angles"),
            chapter(6,"data-handling","Data Handling","statistics"),
            chapter(6,"area-perimeter","Perimeter and Area","area-perimeter"),
            chapter(6,"ratio-proportion","Ratio and Proportion","ratios"),
            chapter(6,"symmetry","Symmetry","symmetry"),
            chapter(6,"practical-geometry","Practical Geometry","constructions"),
            chapter(6,"2d-3d-shapes","Understanding 2D and 3D Shapes","solid-shapes")
        )),
        Map.entry(7, List.of(
            chapter(7,"integers","Integers","integers"),
            chapter(7,"fractions-decimals-rationals","Fractions, Decimals and Rational Numbers","fractions","decimals"),
            chapter(7,"simple-equations","Simple Equations","linear-equations"),
            chapter(7,"lines-angles","Lines and Angles","lines-angles"),
            chapter(7,"triangles","Triangle and Its Properties","triangles"),
            chapter(7,"ratio-applications","Ratio — Applications","ratios","percentages"),
            chapter(7,"data-handling","Data Handling","statistics","probability"),
            chapter(7,"congruency","Congruency of Triangles","triangles"),
            chapter(7,"triangle-construction","Construction of Triangles","constructions"),
            chapter(7,"algebraic-expressions","Algebraic Expressions","algebraic-expressions"),
            chapter(7,"powers-exponents","Powers and Exponents","exponents"),
            chapter(7,"quadrilaterals","Quadrilaterals","quadrilaterals"),
            chapter(7,"area-perimeter","Area and Perimeter","area-perimeter"),
            chapter(7,"2d-3d-shapes","Understanding 2D and 3D Shapes","solid-shapes"),
            chapter(7,"symmetry","Symmetry","symmetry"),
            chapter(7,"fractions","Working with Fractions","fractions"),
            chapter(7,"number-patterns","Patterns and Number Play","number-patterns")
        )),
        Map.entry(8, List.of(
            chapter(8,"rational-numbers","Rational Numbers","fractions","integers"),
            chapter(8,"linear-equations","Linear Equations in One Variable","linear-equations"),
            chapter(8,"understanding-quadrilaterals","Understanding Quadrilaterals","quadrilaterals"),
            chapter(8,"data-handling","Data Handling","statistics","probability"),
            chapter(8,"squares-cubes","Squares, Square Roots, Cubes and Cube Roots","arithmetic","exponents"),
            chapter(8,"comparing-quantities","Comparing Quantities","percentages","profit-loss"),
            chapter(8,"algebraic-expressions","Algebraic Expressions and Identities","algebraic-expressions"),
            chapter(8,"factorisation","Factorisation","factorisation"),
            chapter(8,"exponents-powers","Exponents and Powers","exponents"),
            chapter(8,"direct-inverse-proportion","Direct and Inverse Proportion","ratios"),
            chapter(8,"graphs","Introduction to Graphs","graphs"),
            chapter(8,"mensuration","Mensuration","mensuration"),
            chapter(8,"solid-shapes","Visualising Solid Shapes","solid-shapes"),
            chapter(8,"practical-geometry","Practical Geometry","constructions"),
            chapter(8,"linear-graphs","Linear Graphs","graphs"),
            chapter(8,"number-patterns","Number Patterns","number-patterns")
        )),
        Map.entry(9, List.of(
            chapter(9,"number-systems","Number Systems","integers","fractions"),
            chapter(9,"polynomials","Polynomials","polynomials"),
            chapter(9,"linear-equations","Linear Equations in Two Variables","linear-equations","graphs"),
            chapter(9,"coordinate-geometry","Coordinate Geometry","coordinate-geometry"),
            chapter(9,"geometry-axioms","Geometry, Axioms and Postulates","geometry"),
            chapter(9,"lines-angles","Lines and Angles","lines-angles"),
            chapter(9,"triangles","Triangles and Congruence","triangles"),
            chapter(9,"quadrilaterals","Quadrilaterals","quadrilaterals"),
            chapter(9,"circles","Circles","circle-geometry"),
            chapter(9,"heron-mensuration","Heron's Formula and Mensuration","mensuration"),
            chapter(9,"surface-area-volume","Surface Areas and Volumes","mensuration"),
            chapter(9,"statistics","Statistics","statistics"),
            chapter(9,"probability","Probability","probability"),
            chapter(9,"constructions","Geometrical Constructions","constructions"),
            chapter(9,"sequences","Sequences and Patterns","sequences")
        )),
        Map.entry(10, List.of(
            chapter(10,"real-numbers","Real Numbers","integers","fractions"),
            chapter(10,"polynomials","Polynomials","polynomials"),
            chapter(10,"linear-equations","Pair of Linear Equations","linear-equations"),
            chapter(10,"quadratic-equations","Quadratic Equations","quadratic-equations"),
            chapter(10,"arithmetic-progressions","Arithmetic Progressions","sequences"),
            chapter(10,"triangles","Triangles and Similarity","triangles"),
            chapter(10,"coordinate-geometry","Coordinate Geometry","coordinate-geometry"),
            chapter(10,"trigonometry","Introduction to Trigonometry","trigonometry"),
            chapter(10,"trigonometry-applications","Applications of Trigonometry","trigonometry"),
            chapter(10,"circles","Circles","circle-geometry"),
            chapter(10,"constructions","Geometrical Constructions","constructions"),
            chapter(10,"areas-circles","Areas Related to Circles","circle-geometry","mensuration"),
            chapter(10,"surface-area-volume","Surface Areas and Volumes","mensuration"),
            chapter(10,"statistics","Statistics","statistics"),
            chapter(10,"probability","Probability","probability"),
            chapter(10,"commercial-math","Commercial Mathematics","percentages","profit-loss")
        )),
        Map.entry(11, List.of(
            chapter(11,"sets","Sets","sets"),
            chapter(11,"relations-functions","Relations and Functions","relations-functions"),
            chapter(11,"trigonometric-functions","Trigonometric Functions","trigonometry"),
            chapter(11,"complex-numbers","Complex Numbers","complex-numbers"),
            chapter(11,"linear-inequalities","Linear Inequalities","linear-inequalities"),
            chapter(11,"permutations-combinations","Permutations and Combinations","permutations-combinations"),
            chapter(11,"binomial-theorem","Binomial Theorem","binomial-theorem"),
            chapter(11,"sequences-series","Sequences and Series","sequences"),
            chapter(11,"straight-lines","Straight Lines","coordinate-geometry"),
            chapter(11,"conic-sections","Conic Sections","conic-sections"),
            chapter(11,"3d-geometry","Introduction to 3D Geometry","vectors"),
            chapter(11,"limits-derivatives","Limits and Derivatives","calculus"),
            chapter(11,"statistics","Statistics","statistics"),
            chapter(11,"probability","Probability","probability"),
            chapter(11,"financial-mathematics","Financial Mathematics","percentages","profit-loss")
        )),
        Map.entry(12, List.of(
            chapter(12,"relations-functions","Relations and Functions","relations-functions"),
            chapter(12,"inverse-trigonometry","Inverse Trigonometric Functions","trigonometry"),
            chapter(12,"matrices","Matrices","matrices"),
            chapter(12,"determinants","Determinants","matrices"),
            chapter(12,"continuity-differentiability","Continuity and Differentiability","calculus"),
            chapter(12,"derivative-applications","Applications of Derivatives","calculus"),
            chapter(12,"integrals","Integrals","calculus"),
            chapter(12,"integral-applications","Applications of Integrals","calculus"),
            chapter(12,"differential-equations","Differential Equations","differential-equations"),
            chapter(12,"vectors","Vector Algebra","vectors"),
            chapter(12,"3d-geometry","Three-Dimensional Geometry","vectors"),
            chapter(12,"linear-programming","Linear Programming","linear-programming"),
            chapter(12,"probability","Probability","probability")
        ))
    );

    private static final Map<Integer,List<ChapterSpec>> EVS_CHAPTERS = Map.ofEntries(
        Map.entry(1,chapters(1,"living","Plants and Animals Around Us","evs-living","water","Water We Use","evs-water","safe","Keeping Safe and Healthy","evs-health")),
        Map.entry(2,chapters(2,"living","Living Things and Their Needs","evs-living","water","Water and Weather","evs-water","places","Our Home and Neighbourhood","evs-earth")),
        Map.entry(3,chapters(3,"plants","Plants Around Us","evs-living","animals","Animals and Their Homes","evs-living","water","Water in Daily Life","evs-water","health","Food and Health","evs-health")),
        Map.entry(4,chapters(4,"habitats","Habitats and Adaptations","evs-living","water-cycle","Water Around Us","evs-water","earth","Our Environment","evs-earth","health","Health and Hygiene","evs-health")),
        Map.entry(5,chapters(5,"ecosystems","Living Things and Habitats","evs-living","resources","Water and Natural Resources","evs-water","environment","Protecting Our Environment","evs-earth","health","Food, Health and Safety","evs-health"))
    );
    private static final Map<Integer,List<ChapterSpec>> ENGLISH_CHAPTERS = Map.ofEntries(
        Map.entry(1,chapters(1,"words","Words and Naming","english-vocabulary","sentences","Building Sentences","english-sentences","sounds","Sounds and Spelling","english-grammar")),
        Map.entry(2,chapters(2,"nouns","Naming Words","english-grammar","verbs","Action Words","english-tenses","sentences","Sentences and Questions","english-sentences")),
        Map.entry(3,chapters(3,"grammar","Nouns, Verbs and Adjectives","english-grammar","tenses","Present and Past Actions","english-tenses","vocabulary","Word Meaning","english-vocabulary","punctuation","Capital Letters and Punctuation","english-punctuation")),
        Map.entry(4,chapters(4,"grammar","Parts of Speech","english-grammar","tenses","Tenses and Verbs","english-tenses","sentences","Sentence Types","english-sentences","vocabulary","Synonyms and Antonyms","english-vocabulary")),
        Map.entry(5,chapters(5,"grammar","Grammar in Context","english-grammar","tenses","Verb Tenses","english-tenses","sentences","Sentence Structure","english-sentences","punctuation","Punctuation and Editing","english-punctuation")),
        Map.entry(6,chapters(6,"grammar","Parts of Speech","english-grammar","tenses","Tenses and Subject–Verb Agreement","english-tenses","vocabulary","Vocabulary in Context","english-vocabulary","writing","Sentence Structure and Punctuation","english-sentences","english-punctuation")),
        Map.entry(7,chapters(7,"grammar","Grammar and Usage","english-grammar","tenses","Verbs and Tenses","english-tenses","vocabulary","Word Choice and Meaning","english-vocabulary","writing","Sentence Construction","english-sentences")),
        Map.entry(8,chapters(8,"grammar","Grammar and Clauses","english-grammar","tenses","Tenses and Modals","english-tenses","vocabulary","Vocabulary and Context","english-vocabulary","writing","Sentence Editing and Punctuation","english-punctuation")),
        Map.entry(9,chapters(9,"grammar","Grammar and Usage","english-grammar","tenses","Tenses and Agreement","english-tenses","vocabulary","Vocabulary and Meaning","english-vocabulary","writing","Sentence Transformation","english-sentences")),
        Map.entry(10,chapters(10,"grammar","Grammar and Editing","english-grammar","tenses","Tenses and Modals","english-tenses","vocabulary","Vocabulary in Context","english-vocabulary","writing","Sentence Structure and Punctuation","english-punctuation")),
        Map.entry(11,chapters(11,"grammar","Advanced Grammar and Usage","english-grammar","tenses","Verb Forms and Tenses","english-tenses","vocabulary","Academic Vocabulary","english-vocabulary","writing","Sentence Clarity and Editing","english-sentences")),
        Map.entry(12,chapters(12,"grammar","Advanced Grammar and Usage","english-grammar","tenses","Verb Forms and Tenses","english-tenses","vocabulary","Academic Vocabulary","english-vocabulary","writing","Sentence Clarity and Editing","english-sentences"))
    );
    private static final Map<Integer,List<ChapterSpec>> SCIENCE_CHAPTERS = Map.ofEntries(
        Map.entry(6,chapters(6,"living","Living Things and Their Needs","science-life","materials","Materials Around Us","science-matter","motion","Motion and Measurement","science-force","energy","Light and Energy","science-energy")),
        Map.entry(7,chapters(7,"nutrition","Nutrition and Life Processes","science-life","matter","Heat and Changes in Materials","science-matter","motion","Motion and Time","science-force","light","Light and Its Behaviour","science-energy")),
        Map.entry(8,chapters(8,"cells","Cells and Microorganisms","science-life","materials","Materials and Changes","science-matter","force","Force and Pressure","science-force","sound","Sound and Light","science-energy","environment","Conservation of the Environment","science-environment")),
        Map.entry(9,chapters(9,"matter","Matter and Its Structure","science-matter","cells","Cells and Tissues","science-life","motion","Motion and Laws","science-force","energy","Work and Energy","science-energy","environment","Our Environment","science-environment")),
        Map.entry(10,chapters(10,"reactions","Chemical Reactions and Materials","science-matter","life","Life Processes","science-life","light","Light and Vision","science-energy","electricity","Electricity and Circuits","science-electricity","environment","Environment and Sustainability","science-environment"))
    );
    private static final Map<Integer,List<ChapterSpec>> SOCIAL_CHAPTERS = Map.ofEntries(
        Map.entry(6,chapters(6,"history","Early Societies and Civilisations","social-history","maps","Maps and the Earth","social-geography","community","Community and Government","social-civics","needs","Needs and Resources","social-economics")),
        Map.entry(7,chapters(7,"history","Medieval Societies","social-history","environment","Environment and Settlements","social-geography","government","Democracy and Government","social-civics","economy","Livelihoods and the Economy","social-economics")),
        Map.entry(8,chapters(8,"history","Societies and Change","social-history","resources","Resources and Development","social-geography","constitution","The Constitution and Rights","social-civics","markets","Markets and Production","social-economics")),
        Map.entry(9,chapters(9,"history","Modern History and Change","social-history","climate","Climate and Physical Geography","social-geography","democracy","Democracy and Institutions","social-civics","economy","People and the Economy","social-economics")),
        Map.entry(10,chapters(10,"history","Nationalism and the Modern World","social-history","development","Resources and Development","social-geography","institutions","Power Sharing and Institutions","social-civics","markets","Economic Development","social-economics"))
    );
    private static final Map<Integer,List<ChapterSpec>> COMPUTER_CHAPTERS = Map.ofEntries(
        Map.entry(6,chapters(6,"hardware","Computer Parts and Uses","computer-basics","data","Files and Data","computer-data","coding","Step-by-Step Instructions","computer-coding","safety","Staying Safe Online","computer-safety")),
        Map.entry(7,chapters(7,"hardware","Hardware and Software","computer-basics","data","Representing Information","computer-data","coding","Algorithms and Sequences","computer-coding","safety","Digital Citizenship","computer-safety")),
        Map.entry(8,chapters(8,"systems","Computer Systems","computer-basics","networks","Networks and the Internet","computer-data","coding","Variables and Algorithms","computer-coding","safety","Privacy and Security","computer-safety")),
        Map.entry(9,chapters(9,"systems","Computer Systems and Data","computer-basics","networks","Networks and the Internet","computer-data","coding","Programming Logic","computer-coding","safety","Cyber Safety","computer-safety")),
        Map.entry(10,chapters(10,"systems","Computer Systems and Data","computer-basics","networks","Networks and the Internet","computer-data","coding","Algorithms and Programming","computer-coding","safety","Cybersecurity and Ethics","computer-safety")),
        Map.entry(11,chapters(11,"systems","Computer Systems","computer-basics","data","Data Representation","computer-data","coding","Programming and Algorithms","computer-coding","safety","Networks and Cyber Safety","computer-safety")),
        Map.entry(12,chapters(12,"systems","Computer Systems","computer-basics","data","Data and Networks","computer-data","coding","Programming and Algorithms","computer-coding","safety","Cybersecurity and Ethics","computer-safety"))
    );
    private static final Map<Integer,List<ChapterSpec>> PHYSICS_CHAPTERS = Map.ofEntries(
        Map.entry(11,chapters(11,"motion","Kinematics and Motion","physics-motion","forces","Laws of Motion","physics-motion","energy","Work, Energy and Power","physics-energy","waves","Waves and Oscillations","physics-waves")),
        Map.entry(12,chapters(12,"electricity","Electric Charges and Current","physics-electricity","magnetism","Magnetism and Induction","physics-electricity","optics","Ray and Wave Optics","physics-waves","modern","Modern Physics","physics-energy"))
    );
    private static final Map<Integer,List<ChapterSpec>> CHEMISTRY_CHAPTERS = Map.ofEntries(
        Map.entry(11,chapters(11,"atoms","Atomic Structure and Periodicity","chemistry-atoms","bonding","Chemical Bonding","chemistry-bonding","reactions","Chemical Reactions","chemistry-reactions","equilibrium","Equilibrium and Acids","chemistry-acids")),
        Map.entry(12,chapters(12,"solutions","Solutions and Materials","chemistry-bonding","kinetics","Chemical Kinetics","chemistry-reactions","acids","Acids, Bases and Electrochemistry","chemistry-acids","organic","Organic Chemistry","chemistry-bonding"))
    );
    private static final Map<Integer,List<ChapterSpec>> BIOLOGY_CHAPTERS = Map.ofEntries(
        Map.entry(11,chapters(11,"cells","Cell Structure and Function","biology-cells","plants","Plant Physiology","biology-plants","body","Human Physiology","biology-body","ecology","Ecology","biology-ecology")),
        Map.entry(12,chapters(12,"genetics","Genetics and Evolution","biology-cells","reproduction","Reproduction","biology-body","health","Human Health","biology-body","ecology","Ecology and Environment","biology-ecology"))
    );
    private static final Map<Integer,List<ChapterSpec>> ECONOMICS_CHAPTERS = Map.ofEntries(
        Map.entry(11,chapters(11,"markets","Markets and Choices","economics-markets","demand","Demand and Supply","economics-demand","money","Money and Banking","economics-money","income","Measuring National Income","economics-income")),
        Map.entry(12,chapters(12,"markets","Market Forms and Choices","economics-markets","demand","Demand and Supply","economics-demand","money","Money and Banking","economics-money","income","National Income and the Economy","economics-income"))
    );
    private static final Map<String,Map<Integer,List<ChapterSpec>>> SCHOOL_CHAPTERS = Map.ofEntries(
        Map.entry("evs",EVS_CHAPTERS),Map.entry("english",ENGLISH_CHAPTERS),Map.entry("science",SCIENCE_CHAPTERS),
        Map.entry("social",SOCIAL_CHAPTERS),Map.entry("computer",COMPUTER_CHAPTERS),Map.entry("physics",PHYSICS_CHAPTERS),
        Map.entry("chemistry",CHEMISTRY_CHAPTERS),Map.entry("biology",BIOLOGY_CHAPTERS),Map.entry("economics",ECONOMICS_CHAPTERS)
    );
    private static final Map<String, List<ChapterSpec>> OTHER_CHAPTERS = Map.of(
        "quant", List.of(
            new ChapterSpec("business-math", "Percentages and Profit or Loss", List.of("percentages", "profit-loss")),
            new ChapterSpec("work-rates", "Work and Rates", List.of("time-work"))
        ),
        "logic", List.of(
            new ChapterSpec("patterns", "Patterns and Codes", List.of("number-series", "coding")),
            new ChapterSpec("spatial-reasoning", "Direction and Spatial Reasoning", List.of("directions"))
        )
    );

    private final Db db;
    public CurriculumService(Db db) { this.db = db; }

    public Catalog catalog(String subject, Integer classLevel) {
        if (classLevel != null && !CLASS_LEVELS.contains(classLevel))
            throw new ApiException(400, "Choose a class from 1 to 12");
        var names = new HashMap<String, String>();
        for (var row : db.rows("select id,name from topics where subject_id=? order by id", subject))
            names.put(Db.str(row, "id"), Db.str(row, "name"));
        List<ChapterSpec> specs;
        if (subject.equals("math"))
            specs = classLevel == null ? MATH_CHAPTERS.values().stream().flatMap(List::stream).toList() : MATH_CHAPTERS.get(classLevel);
        else if (SCHOOL_CHAPTERS.containsKey(subject)) {
            var byClass=SCHOOL_CHAPTERS.get(subject);
            specs=classLevel==null?byClass.values().stream().flatMap(List::stream).toList():byClass.get(classLevel);
        } else specs = OTHER_CHAPTERS.get(subject);
        if (specs == null || names.isEmpty()) throw new ApiException(404, "Subject not found");
        var chapters = specs.stream().map(chapter -> new Chapter(
            chapter.id(), chapter.name(), chapter.topicIds().stream()
                .filter(names::containsKey)
                .map(id -> new Topic(id, names.get(id))).toList()
        )).filter(chapter -> !chapter.topics().isEmpty()).toList();
        var levels=subject.equals("math")?CLASS_LEVELS
            :SCHOOL_CHAPTERS.containsKey(subject)?SCHOOL_CHAPTERS.get(subject).keySet().stream().sorted().toList()
            :List.of(6,7,8,9,10,11,12);
        return new Catalog(levels, chapters);
    }

    public String chapterName(String subject, Integer classLevel, String chapterId) {
        if (chapterId == null) return null;
        var specs = subject.equals("math")
            ? (classLevel == null ? MATH_CHAPTERS.values().stream().flatMap(List::stream).toList() : MATH_CHAPTERS.getOrDefault(classLevel, List.of()))
            : SCHOOL_CHAPTERS.containsKey(subject)
                ? (classLevel == null ? SCHOOL_CHAPTERS.get(subject).values().stream().flatMap(List::stream).toList() : SCHOOL_CHAPTERS.get(subject).getOrDefault(classLevel,List.of()))
                : OTHER_CHAPTERS.getOrDefault(subject, List.of());
        return specs.stream().filter(chapter -> chapter.id().equals(chapterId))
            .map(ChapterSpec::name).findFirst().orElse(null);
    }

    public List<String> selectedTopics(String subject, Integer classLevel, String chapterId, String topicId) {
        var chapters = catalog(subject, classLevel).chapters();
        if (chapterId != null) {
            chapters = chapters.stream().filter(chapter -> chapter.id().equals(chapterId)).toList();
            if (chapters.isEmpty()) throw new ApiException(400, "Chapter does not belong to this subject and class");
        }
        var topics = chapters.stream().flatMap(chapter -> chapter.topics().stream()).toList();
        if (topicId != null) {
            topics = topics.stream().filter(topic -> topic.id().equals(topicId)).toList();
            if (topics.isEmpty()) throw new ApiException(400, "Topic does not belong to this chapter");
        }
        return topics.stream().map(Topic::id).toList();
    }
}
