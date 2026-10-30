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
