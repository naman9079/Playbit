import { Calculator, BrainCircuit, ChartNoAxesCombined } from "lucide-react";
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
