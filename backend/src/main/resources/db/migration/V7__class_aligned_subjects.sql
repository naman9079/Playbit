INSERT INTO subjects(id,name,slug) VALUES
 ('evs','Environmental Studies','environmental-studies'),
 ('science','Science','science'),
 ('english','English','english'),
 ('social','Social Studies','social-studies'),
 ('computer','Computer Science','computer-science'),
 ('physics','Physics','physics'),
 ('chemistry','Chemistry','chemistry'),
 ('biology','Biology','biology'),
 ('economics','Economics','economics');

INSERT INTO topics(id,subject_id,name) VALUES
 ('evs-living','evs','Living Things'),('evs-water','evs','Water'),('evs-earth','evs','Earth and Environment'),('evs-health','evs','Health and Safety'),
 ('science-life','science','Life Science'),('science-matter','science','Matter and Materials'),('science-force','science','Force and Motion'),('science-energy','science','Energy and Light'),('science-electricity','science','Electricity'),('science-environment','science','Environment'),
 ('english-grammar','english','Grammar'),('english-tenses','english','Tenses and Verbs'),('english-vocabulary','english','Vocabulary'),('english-sentences','english','Sentences'),('english-punctuation','english','Punctuation'),
 ('social-history','social','History'),('social-geography','social','Geography'),('social-civics','social','Civics'),('social-economics','social','Economics'),
 ('computer-basics','computer','Computer Basics'),('computer-coding','computer','Coding and Algorithms'),('computer-data','computer','Data and Networks'),('computer-safety','computer','Digital Safety'),
 ('physics-motion','physics','Motion and Forces'),('physics-energy','physics','Work and Energy'),('physics-electricity','physics','Electricity'),('physics-waves','physics','Waves and Light'),
 ('chemistry-atoms','chemistry','Atoms and the Periodic Table'),('chemistry-reactions','chemistry','Chemical Reactions'),('chemistry-acids','chemistry','Acids and Bases'),('chemistry-bonding','chemistry','Bonding and Materials'),
 ('biology-cells','biology','Cells and Genetics'),('biology-plants','biology','Plants and Life Processes'),('biology-body','biology','Human Body'),('biology-ecology','biology','Ecology'),
 ('economics-markets','economics','Markets and Choices'),('economics-money','economics','Money and Banking'),('economics-demand','economics','Demand and Supply'),('economics-income','economics','National Income');

INSERT INTO user_subject_ratings(user_id,subject_id)
SELECT p.id,s.id FROM profiles p CROSS JOIN subjects s
ON CONFLICT (user_id,subject_id) DO NOTHING;
