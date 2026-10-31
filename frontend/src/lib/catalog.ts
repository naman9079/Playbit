import { Atom, BrainCircuit, Calculator, ChartNoAxesCombined, Code2, FlaskConical, Globe2, Languages, Leaf, Microscope } from "lucide-react";
export const subjects = [
  {
    id: "math",
    name: "Mathematics",
    short: "Math",
    description: "Find your flow in numbers.",
    topics: ["Fractions", "Algebra", "Exponents"],
    icon: Calculator,
    color: "purple",
    symbol: "x²",
    minClass: 1, maxClass: 12,
  },
  {
    id: "quant",
    name: "Quantitative Aptitude",
    short: "Quant",
    description: "Turn everyday problems into wins.",
    topics: ["Percentages", "Profit and Loss", "Time and Work"],
    icon: ChartNoAxesCombined,
    color: "cyan",
    symbol: "%",
    minClass: 6, maxClass: 12,
  },
  {
    id: "logic",
    name: "Logical Reasoning",
    short: "Logic",
    description: "Think a little outside the grid.",
    topics: ["Number Series", "Direction Sense", "Coding-Decoding"],
    icon: BrainCircuit,
    color: "lime",
    symbol: "↗",
    minClass: 6, maxClass: 12,
  },
  {
    id: "evs", name: "Environmental Studies", short: "EVS",
    description: "Explore plants, animals, health, and the world around you.",
    topics: ["Living Things", "Water", "Earth", "Health"], icon: Leaf,
    color: "lime", symbol: "🌿", minClass: 1, maxClass: 5,
  },
  {
    id: "science", name: "Science", short: "Science",
    description: "Build scientific thinking from everyday observations.",
    topics: ["Life Science", "Matter", "Force", "Energy"], icon: Microscope,
    color: "cyan", symbol: "⚛", minClass: 6, maxClass: 10,
  },
  {
    id: "english", name: "English", short: "English",
    description: "Practice grammar, vocabulary, and clear sentences.",
    topics: ["Grammar", "Tenses", "Vocabulary", "Punctuation"], icon: Languages,
    color: "purple", symbol: "Aa", minClass: 1, maxClass: 12,
  },
  {
    id: "social", name: "Social Studies", short: "Social Studies",
    description: "Explore history, geography, civics, and economics.",
    topics: ["History", "Geography", "Civics", "Economics"], icon: Globe2,
    color: "pink", symbol: "◎", minClass: 6, maxClass: 10,
  },
  {
    id: "computer", name: "Computer Science", short: "Computer Science",
    description: "Learn computing, coding, data, and online safety.",
    topics: ["Computer Basics", "Coding", "Data", "Internet Safety"], icon: Code2,
    color: "cyan", symbol: "</>", minClass: 6, maxClass: 12,
  },
  {
    id: "physics", name: "Physics", short: "Physics",
    description: "Use models and equations to explain the physical world.",
    topics: ["Motion", "Forces", "Electricity", "Waves"], icon: Atom,
    color: "purple", symbol: "F=ma", minClass: 11, maxClass: 12,
  },
  {
    id: "chemistry", name: "Chemistry", short: "Chemistry",
    description: "Understand atoms, reactions, acids, and materials.",
    topics: ["Atoms", "Reactions", "Acids and Bases", "Bonding"], icon: FlaskConical,
    color: "lime", symbol: "H₂O", minClass: 11, maxClass: 12,
  },
  {
    id: "biology", name: "Biology", short: "Biology",
    description: "Explore cells, living systems, and ecosystems.",
    topics: ["Cells", "Human Body", "Plants", "Ecology"], icon: Microscope,
    color: "lime", symbol: "DNA", minClass: 11, maxClass: 12,
  },
  {
    id: "economics", name: "Economics", short: "Economics",
    description: "Study choices, markets, money, and demand.",
    topics: ["Markets", "Money", "Demand", "National Income"], icon: ChartNoAxesCombined,
    color: "pink", symbol: "₹", minClass: 11, maxClass: 12,
  },
];
export const subjectName = (id: string) =>
  subjects.find((s) => s.id === id)?.name || id;

const chapterNames: Record<string, string> = {
  "number-sense": "Number Sense",
  algebra: "Algebra and Powers",
  "business-math": "Percentages and Profit or Loss",
  "work-rates": "Work and Rates",
  patterns: "Patterns and Codes",
  "spatial-reasoning": "Direction and Spatial Reasoning",
};
const topicNames: Record<string, string> = {
  fractions: "Fractions",
  algebra: "Algebra",
  exponents: "Exponents",
  percentages: "Percentages",
  "profit-loss": "Profit and Loss",
  "time-work": "Time and Work",
  "number-series": "Number Series",
  directions: "Direction Sense",
  coding: "Coding-Decoding",
};
export function curriculumScope(match: Record<string, any>) {
  const parts = [
    match.class_level ? `Class ${match.class_level}` : "All classes",
    match.chapter_id ? match.chapter_name || chapterNames[match.chapter_id] || match.chapter_id : null,
    match.topic_id ? topicNames[match.topic_id] || match.topic_id : null,
  ].filter(Boolean);
  return parts.join(" · ");
}
