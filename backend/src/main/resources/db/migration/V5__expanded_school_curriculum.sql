ALTER TABLE matches DROP CONSTRAINT IF EXISTS matches_class_level_check;
ALTER TABLE matches ADD CONSTRAINT matches_class_level_check CHECK (class_level BETWEEN 1 AND 12);
ALTER TABLE questions DROP CONSTRAINT IF EXISTS questions_class_level_check;
ALTER TABLE questions ADD CONSTRAINT questions_class_level_check CHECK (class_level BETWEEN 1 AND 12);
ALTER TABLE matchmaking_queue DROP CONSTRAINT IF EXISTS matchmaking_queue_class_level_check;
ALTER TABLE matchmaking_queue ADD CONSTRAINT matchmaking_queue_class_level_check CHECK (class_level BETWEEN 1 AND 12);

INSERT INTO topics(id,subject_id,name) VALUES
 ('large-numbers','math','Large Numbers'),
 ('whole-numbers','math','Whole Numbers'),
 ('arithmetic','math','Arithmetic and Operations'),
 ('decimals','math','Decimals'),
 ('number-patterns','math','Patterns and Sequences'),
 ('integers','math','Integers'),
 ('lines-angles','math','Lines and Angles'),
 ('angles','math','Angles'),
 ('geometry','math','Geometry'),
 ('statistics','math','Data Handling and Statistics'),
 ('area-perimeter','math','Area and Perimeter'),
 ('ratios','math','Ratio and Proportion'),
 ('symmetry','math','Symmetry'),
 ('constructions','math','Geometrical Constructions'),
 ('solid-shapes','math','Solid Shapes'),
 ('linear-equations','math','Linear Equations'),
 ('triangles','math','Triangles'),
 ('probability','math','Probability'),
 ('algebraic-expressions','math','Algebraic Expressions'),
 ('quadrilaterals','math','Quadrilaterals'),
 ('factorisation','math','Factorisation'),
 ('graphs','math','Graphs'),
 ('mensuration','math','Mensuration'),
 ('polynomials','math','Polynomials'),
 ('coordinate-geometry','math','Coordinate Geometry'),
 ('circle-geometry','math','Circles and Circle Geometry'),
 ('sequences','math','Sequences and Series'),
 ('quadratic-equations','math','Quadratic Equations'),
 ('trigonometry','math','Trigonometry'),
 ('sets','math','Sets'),
 ('relations-functions','math','Relations and Functions'),
 ('complex-numbers','math','Complex Numbers'),
 ('linear-inequalities','math','Linear Inequalities'),
 ('permutations-combinations','math','Permutations and Combinations'),
 ('binomial-theorem','math','Binomial Theorem'),
 ('conic-sections','math','Conic Sections'),
 ('vectors','math','Vectors and Three-Dimensional Geometry'),
 ('calculus','math','Calculus'),
 ('differential-equations','math','Differential Equations'),
 ('matrices','math','Matrices and Determinants'),
 ('linear-programming','math','Linear Programming')
ON CONFLICT(id) DO NOTHING;
