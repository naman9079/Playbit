package com.playbit.service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class QuestionGenerationService {
    public record Question(String topic, int difficulty, String text, List<String> options, String answer, String formula, List<String> steps, String explanation, String fingerprint) {}
    private record Fact(String prompt,String answer,List<String> distractors,String formula,List<String> steps,String explanation) {}
    public Question generate(String topic,int difficulty,Random rng) {
        return generate(topic,difficulty,null,rng);
    }
    public Question generate(String topic,int difficulty,Integer classLevel,Random rng) {
        if(difficulty<1 || difficulty>3) throw new IllegalArgumentException("Difficulty");
        if(classLevel!=null && (classLevel<1 || classLevel>12)) throw new IllegalArgumentException("Class level");
        if(isSchoolTopic(topic)) return generateSchoolQuestion(topic,difficulty,classLevel,rng);
        double classScale=classLevel==null?0.55:0.55+(classLevel-1)*0.15;
        int a=2+rng.nextInt(Math.max(1,(int)Math.round(6*difficulty*classScale)));
        int b=2+rng.nextInt(Math.max(1,(int)Math.round(5*difficulty*classScale)));
        int c=2+rng.nextInt(Math.max(1,(int)Math.round(7*difficulty*classScale)));
        String text,formula,explanation; List<String> steps; int answer;
        switch(topic) {
            case "fractions" -> { int numerator=a+b; answer=numerator;
                text=""+a+"/"+c+" + "+b+"/"+c+" = ?/"+c+". Find the missing numerator.";
                formula="a/c + b/c = (a + b)/c"; steps=List.of("The denominators are equal: "+c+".","Add the numerators: "+a+" + "+b+" = "+answer+".","Keep the denominator unchanged."); explanation="Only add numerators when the denominators are equal."; }
            case "algebra" -> { answer=c; int rhs=a*c+b; text="Solve for x: "+a+"x + "+b+" = "+rhs+".";
                formula="ax + b = c ⇒ x = (c − b) / a"; steps=List.of("Subtract "+b+" from both sides: "+a+"x = "+(rhs-b)+".","Divide by "+a+": x = "+c+"."); explanation="Apply the same operation to both sides to preserve equality."; }
            case "exponents" -> { int base=2+rng.nextInt(8*difficulty), e=1+rng.nextInt(difficulty+1), f=1+rng.nextInt(difficulty+1); answer=e+f;
                text=base+"^"+e+" × "+base+"^"+f+" = "+base+"^n. What is n?"; formula="a^m × a^n = a^(m+n)";
                steps=List.of("Both factors have base "+base+".","Add the powers: "+e+" + "+f+" = "+answer+"."); explanation="When multiplying powers with the same base, add exponents; do not multiply them."; }
            case "percentages" -> { int percent=a*5, value=b*20; answer=percent*value/100; text="What is "+percent+"% of "+value+"?";
                formula="Percentage amount = percent × whole / 100"; steps=List.of("Convert "+percent+"% to "+percent+"/100.","Multiply: "+percent+" × "+value+" / 100 = "+answer+"."); explanation="A percentage is a fraction out of one hundred."; }
            case "profit-loss" -> { int cost=a*100, percent=b*5, selling=cost+cost*percent/100; answer=percent;
                text="A player buys a headset for ₹"+cost+" and sells it for ₹"+selling+". What is the profit percentage?"; formula="Profit % = (selling price − cost price) / cost price × 100";
                steps=List.of("Profit = "+selling+" − "+cost+" = "+(selling-cost)+".","Divide by cost price, then multiply by 100: "+(selling-cost)+" / "+cost+" × 100 = "+percent+"%."); explanation="Profit percentage uses cost price as its denominator, not selling price."; }
            case "time-work" -> { int workers=a, days=b*c; answer=days; text=workers+" identical workers finish a job in "+b+" days. How many days would "+workers+" workers take to finish "+c+" identical jobs?";
                formula="Time = time per job × number of jobs (fixed workforce)"; steps=List.of("The workforce stays the same, so one job takes "+b+" days.","For "+c+" jobs: "+b+" × "+c+" = "+answer+" days."); explanation="With a fixed workforce and constant productivity, time scales with the amount of work."; }
            case "number-series" -> { answer=a+4*b; text="This sequence adds the same number each time: "+a+", "+(a+b)+", "+(a+2*b)+", "+(a+3*b)+", ?";
                formula="Next term = current term + common difference"; steps=List.of("Common difference = "+(a+b)+" − "+a+" = "+b+".","Next term = "+(a+3*b)+" + "+b+" = "+answer+"."); explanation="Check that the difference is constant between every adjacent pair."; }
            case "directions" -> { answer=b; text="You walk "+a+" m north, "+b+" m east, then "+a+" m south. How far east of the start are you (in m)?";
                formula="Net displacement = sum of signed movements on each axis"; steps=List.of("North and south movements cancel: "+a+" − "+a+" = 0.","The east displacement remains "+b+" m."); explanation="Displacement depends on direction; it differs from total distance walked."; }
            case "coding" -> { int letter=rng.nextInt(20), shift=1+rng.nextInt(5); answer=letter+shift+1;
                text="In a code, A = 1, B = 2, …, Z = 26. Add "+shift+" to a letter's value. What is the code for "+(char)('A'+letter)+"?";
                formula="Code = alphabet position + shift"; steps=List.of((char)('A'+letter)+" has position "+(letter+1)+".","Add "+shift+": "+(letter+1)+" + "+shift+" = "+answer+"."); explanation="Count alphabet positions from one before applying the stated shift."; }
            case "large-numbers", "whole-numbers", "arithmetic" -> { int x=10+rng.nextInt(Math.max(2,(int)(100*classScale*difficulty))); int y=1+rng.nextInt(x); answer=x+y;
                text="A library has "+x+" books and receives "+y+" more. How many books does it have now?"; formula="Total = starting amount + amount added";
                steps=List.of("Start with "+x+" books.","Add "+y+": "+x+" + "+y+" = "+answer+"."); explanation="Addition combines two quantities."; }
            case "decimals" -> { int x=10+rng.nextInt(90), y=10+rng.nextInt(90); answer=x+y;
                text="Add the tenths: "+(x/10)+"."+(x%10)+" + "+(y/10)+"."+(y%10)+". Give your answer in tenths."; formula="Add like place values";
                steps=List.of("Write both amounts in tenths: "+x+" tenths + "+y+" tenths.","Add: "+x+" + "+y+" = "+answer+" tenths."); explanation="Align decimal place values before adding."; }
            case "integers" -> { int x=1+rng.nextInt(15), y=1+rng.nextInt(15); answer=x-y;
                text="Evaluate "+x+" + (−"+y+")."; formula="Adding a negative is subtraction";
                steps=List.of("Rewrite as subtraction: "+x+" − "+y+".","The result is "+answer+"."); explanation="Adding a negative number moves left on the number line."; }
            case "angles", "lines-angles" -> { int x=20+2*rng.nextInt(30); answer=180-x;
                text="Two angles form a straight line. One is "+x+"°. Find the other angle."; formula="Angles on a straight line sum to 180°";
                steps=List.of("A straight angle is 180°.","Subtract: 180° − "+x+"° = "+answer+"°."); explanation="Adjacent angles on a straight line are supplementary."; }
            case "statistics" -> { int x=2+rng.nextInt(10), y=2+rng.nextInt(10), mean=4+rng.nextInt(8), z=3*mean-x-y; if(z<1) {mean+=4;z=3*mean-x-y;} answer=mean;
                text="Find the mean of these three values: "+x+", "+y+", "+z+"."; formula="Mean = sum of observations / number of observations";
                steps=List.of("Add the values: "+x+" + "+y+" + "+z+" = "+(x+y+z)+".","Divide by 3: "+(x+y+z)+" / 3 = "+answer+"."); explanation="The arithmetic mean is the total divided by the number of values."; }
            case "area-perimeter" -> { int x=2+rng.nextInt(15), y=2+rng.nextInt(15); answer=2*(x+y);
                text="Find the perimeter of a rectangle with length "+x+" cm and width "+y+" cm."; formula="P = 2(length + width)";
                steps=List.of("Add length and width: "+x+" + "+y+" = "+(x+y)+".","Double it: 2 × "+(x+y)+" = "+answer+" cm."); explanation="A rectangle has two sides of each dimension."; }
            case "mensuration" -> { int x=2+rng.nextInt(12), y=2+rng.nextInt(12); answer=x*y;
                text="Find the area of a rectangle measuring "+x+" cm by "+y+" cm."; formula="Area = length × width";
                steps=List.of("Multiply the side lengths: "+x+" × "+y+".","Area = "+answer+" cm²."); explanation="Rectangle area counts square units inside its boundary."; }
            case "solid-shapes" -> { int x=2+rng.nextInt(8), y=2+rng.nextInt(8), z=2+rng.nextInt(8); answer=x*y*z;
                text="Find the volume of a cuboid with sides "+x+" cm, "+y+" cm and "+z+" cm."; formula="Volume = length × width × height";
                steps=List.of("Multiply the three dimensions: "+x+" × "+y+" × "+z+".","Volume = "+answer+" cm³."); explanation="Volume measures the space enclosed by a solid."; }
            case "triangles" -> { int x=2+rng.nextInt(15), y=2+rng.nextInt(15); answer=x*y;
                text="Find the area of a triangle with base "+(2*x)+" cm and perpendicular height "+y+" cm."; formula="Area = 1/2 × base × height";
                steps=List.of("Substitute the measurements: 1/2 × "+(2*x)+" × "+y+".","The area is "+answer+" cm²."); explanation="Triangle area is half the product of its base and perpendicular height."; }
            case "ratios" -> { int x=2+rng.nextInt(8), factor=2+rng.nextInt(6); answer=x*factor;
                text="A recipe uses flour and water in the ratio 1 : "+x+". If flour is "+factor+" cups, how many cups of water are needed?"; formula="Scale both parts of a ratio by the same factor";
                steps=List.of("The flour part is scaled by "+factor+".","Water = "+x+" × "+factor+" = "+answer+" cups."); explanation="Equivalent ratios keep the same multiplier for each part."; }
            case "probability" -> { int x=2+rng.nextInt(8); answer=x;
                text="A bag has "+x+" red counters and 3 blue counters. How many outcomes in the bag are red?"; formula="Count the outcomes matching the event";
                steps=List.of("Count the red counters.","There are "+answer+" red outcomes."); explanation="Probability is the number of favorable outcomes divided by all equally likely outcomes."; }
            case "number-patterns", "sequences" -> { int x=1+rng.nextInt(12), d=1+rng.nextInt(8); answer=x+4*d;
                text="A sequence starts "+x+", "+(x+d)+", "+(x+2*d)+", ... What is its fifth term?"; formula="Term = first term + (position − 1) × common difference";
                steps=List.of("The common difference is "+d+".","Fifth term = "+x+" + 4 × "+d+" = "+answer+"."); explanation="An arithmetic sequence changes by a constant amount each step."; }
            case "coordinate-geometry" -> { int scale=1+rng.nextInt(9), x=3*scale, y=4*scale; answer=5*scale;
                text="Find the distance from (0, 0) to ("+x+", "+y+")."; formula="Distance = √((x₂ − x₁)² + (y₂ − y₁)²)";
                steps=List.of("Compute √("+x+"² + "+y+"²).","The distance is "+answer+" units."); explanation="The distance formula follows from the Pythagorean theorem."; }
            case "graphs" -> { int slope=1+rng.nextInt(5), intercept=1+rng.nextInt(8), x=2+rng.nextInt(6); answer=slope*x+intercept;
                text="For the line y = "+slope+"x + "+intercept+", find y when x = "+x+"."; formula="Substitute x into y = mx + c";
                steps=List.of("Substitute x = "+x+": y = "+slope+" × "+x+" + "+intercept+".","y = "+answer+"."); explanation="A point on a graph satisfies the equation of its line."; }
            case "quadrilaterals" -> { int x=50+rng.nextInt(30), y=50+rng.nextInt(30), z=50+rng.nextInt(30); answer=360-x-y-z;
                text="Three angles of a quadrilateral are "+x+"°, "+y+"° and "+z+"°. Find the fourth angle."; formula="Interior angles of a quadrilateral sum to 360°";
                steps=List.of("Subtract the known angles from 360°.","360° − "+x+"° − "+y+"° − "+z+"° = "+answer+"°."); explanation="The interior angles of every quadrilateral sum to 360 degrees."; }
            case "constructions" -> { int length=2*(2+rng.nextInt(15)); answer=length/2;
                text="A line segment is "+length+" cm long. What is the length of each half after constructing its perpendicular bisector?"; formula="A perpendicular bisector divides a segment into two equal parts";
                steps=List.of("The two parts have equal length.",length+" ÷ 2 = "+answer+" cm."); explanation="A perpendicular bisector passes through the midpoint of the segment."; }
            case "geometry", "symmetry" -> { int width=2+rng.nextInt(12), height=1+rng.nextInt(11); if(width==height) width++;
                answer=2; text="How many lines of symmetry does a non-square rectangle measuring "+width+" by "+height+" units have?";
                formula="A rectangle is symmetric across its horizontal and vertical midlines";
                steps=List.of("Reflect the rectangle across its horizontal midline.","Reflect it across its vertical midline: there are 2 lines."); explanation="A non-square rectangle has two reflection symmetries."; }
            case "circle-geometry" -> { int radius=7*(1+rng.nextInt(4)); answer=22*radius*radius/7;
                text="Using π = 22/7, find the area of a circle with radius "+radius+" cm."; formula="Area = πr²";
                steps=List.of("Substitute the radius: (22/7) × "+radius+"².","The area is "+answer+" cm²."); explanation="The area of a circle is pi times the square of its radius."; }
            case "algebraic-expressions" -> { answer=a+b;
                text="Simplify the expression "+a+"x + "+b+"x. What is the coefficient of x?"; formula="Combine like terms by adding their coefficients";
                steps=List.of("Both terms contain x.","Add the coefficients: "+a+" + "+b+" = "+answer+"."); explanation="Like terms can be combined by adding their coefficients."; }
            case "factorisation" -> { int factor=2+rng.nextInt(8), x=2+rng.nextInt(8), y=2+rng.nextInt(8); answer=factor;
                text="What is the greatest common factor of "+(factor*x)+"x and "+(factor*y)+"y?"; formula="Factor out the greatest common divisor of the coefficients";
                steps=List.of("Both coefficients are multiples of "+factor+".","Their greatest common factor is "+answer+"."); explanation="Factorisation rewrites an expression as a product of its common factor and remaining terms."; }
            case "polynomials" -> { int x=2+rng.nextInt(6), constant=1+rng.nextInt(8); answer=x*x+constant;
                text="Evaluate p(x) = x² + "+constant+" at x = "+x+"."; formula="Substitute the input and apply the exponent before addition";
                steps=List.of("Substitute x = "+x+": "+x+"² + "+constant+".",x+"² + "+constant+" = "+answer+"."); explanation="A polynomial's value is found by substituting the input for its variable."; }
            case "linear-inequalities" -> { int boundary=2+rng.nextInt(15), shift=1+rng.nextInt(8); answer=boundary-shift;
                text="Solve the inequality x + "+shift+" < "+boundary+". What is the boundary value for x?"; formula="Subtract the same quantity from both sides";
                steps=List.of("Subtract "+shift+" from both sides.","The boundary is x = "+answer+"; solutions satisfy x < "+answer+"."); explanation="Adding or subtracting the same number preserves the inequality direction."; }
            case "linear-equations" -> { answer=c; int rhs=a*c+b;
                text="Solve for x: "+a+"x + "+b+" = "+rhs+"."; formula="ax + b = c ⇒ x = (c − b) / a";
                steps=List.of("Subtract "+b+" from both sides: "+a+"x = "+(rhs-b)+".","Divide by "+a+": x = "+c+"."); explanation="Apply the same operation to both sides to preserve equality."; }
            case "quadratic-equations" -> { int root1=1+rng.nextInt(8), root2=1+rng.nextInt(8); answer=Math.max(root1,root2);
                text="The equation (x − "+root1+")(x − "+root2+") = 0 has two roots. What is the larger root?"; formula="If ab = 0, then a = 0 or b = 0";
                steps=List.of("Set each factor to zero: x = "+root1+" or x = "+root2+".","The larger root is "+answer+"."); explanation="A factored quadratic is zero when at least one factor is zero."; }
            case "sets" -> { int x=3+rng.nextInt(8), y=3+rng.nextInt(8), shared=1+rng.nextInt(2); answer=x+y-shared;
                text="Set A has "+x+" elements and set B has "+y+". They share "+shared+" elements. How many are in A ∪ B?"; formula="n(A ∪ B) = n(A) + n(B) − n(A ∩ B)";
                steps=List.of("Add both set sizes: "+x+" + "+y+" = "+(x+y)+".","Subtract the shared elements once: "+(x+y)+" − "+shared+" = "+answer+"."); explanation="The intersection is counted in both set sizes, so subtract it once."; }
            case "relations-functions" -> { answer=a+b;
                text="A function is defined by f(x) = x + "+b+". Find f("+a+")."; formula="Substitute the input into the function rule";
                steps=List.of("Replace x with "+a+".","f("+a+") = "+a+" + "+b+" = "+answer+"."); explanation="A function rule maps each input to its corresponding output."; }
            case "complex-numbers" -> { int real1=a, real2=b; answer=real1+real2;
                text="Find the real part of ("+real1+" + 2i) + ("+real2+" − 3i)."; formula="Add the real parts and the imaginary parts separately";
                steps=List.of("Add the real parts: "+real1+" + "+real2+" = "+answer+".","The real part is "+answer+"."); explanation="Complex numbers are added by combining like real and imaginary parts."; }
            case "permutations-combinations" -> { int n=4+rng.nextInt(6); answer=n*(n-1)/2;
                text="How many different pairs can be chosen from "+n+" students?"; formula="C(n, 2) = n(n − 1) / 2";
                steps=List.of("Substitute n = "+n+".","Compute "+n+" × "+(n-1)+" / 2 = "+answer+"."); explanation="A pair is an unordered selection of two students."; }
            case "binomial-theorem" -> { int n=2+rng.nextInt(6); answer=n;
                text="What is the coefficient of x in the expansion of (1 + x)^"+n+"?"; formula="The coefficient of x in (1 + x)^n is n";
                steps=List.of("Choose x from one of the "+n+" factors.","There are "+n+" choices, so the coefficient is "+answer+"."); explanation="The linear term occurs once for each choice of which factor contributes x."; }
            case "conic-sections" -> { int radius=2+rng.nextInt(15); answer=1;
                text="Which conic is represented by x² + y² = "+(radius*radius)+"? Choose 1 for circle, 2 for ellipse, or 3 for parabola."; formula="Equal squared x and y terms with the same sign describe a circle";
                steps=List.of("The x² and y² coefficients are equal.","This equation describes a circle, coded as "+answer+"."); explanation="A circle is the set of points at a fixed distance from its centre."; }
            case "vectors" -> { int scale=1+rng.nextInt(9), x=3*scale, y=4*scale; answer=5*scale;
                text="Find the magnitude of vector ("+x+", "+y+")."; formula="||(x, y)|| = √(x² + y²)";
                steps=List.of("Compute √("+x+"² + "+y+"²).","√("+(x*x+y*y)+") = "+answer+"."); explanation="Vector magnitude is its Euclidean length."; }
            case "calculus" -> { int power=2+rng.nextInt(5); answer=power;
                text="For f(x) = x^"+power+", what is the coefficient of x^"+(power-1)+" in f′(x)?"; formula="d(x^n)/dx = n x^(n−1)";
                steps=List.of("Differentiate x^"+power+" to get "+power+"x^"+(power-1)+".","The coefficient is "+answer+"."); explanation="The power rule brings the exponent down as a coefficient."; }
            case "differential-equations" -> { answer=1+rng.nextInt(8);
                text="If dy/dx = "+answer+", what is the slope of every solution curve?"; formula="A constant derivative is the slope of the solution";
                steps=List.of("The derivative dy/dx is given as "+answer+".","So each solution has slope "+answer+"."); explanation="The derivative gives the instantaneous slope of the function."; }
            case "matrices" -> { int x=a, y=b; answer=x+y;
                text="Add the top-left entries of matrices A and B if those entries are "+x+" and "+y+"."; formula="Add corresponding entries of matrices";
                steps=List.of("The entries occupy the same position.",x+" + "+y+" = "+answer+"."); explanation="Matrix addition combines entries in matching positions."; }
            case "linear-programming" -> { int cap=5+rng.nextInt(8); answer=cap;
                text="Maximize P = x + y subject to x ≥ 0, y ≥ 0 and x + y ≤ "+cap+". What is the maximum P?"; formula="The maximum occurs on the boundary x + y = "+cap;
                steps=List.of("The constraint gives P = x + y ≤ "+cap+".","The boundary is attainable, so the maximum is "+answer+"."); explanation="A linear objective reaches this maximum at a feasible boundary point."; }
            case "trigonometry" -> { int scale=1+rng.nextInt(9); answer=3*scale;
                text="In a "+(3*scale)+"-"+(4*scale)+"-"+(5*scale)+" right triangle, find "+(5*scale)+" × sin(angle opposite the shortest side)."; formula="sin θ = opposite / hypotenuse";
                steps=List.of("sin θ = "+(3*scale)+" / "+(5*scale)+".",(5*scale)+" × sin θ = "+answer+"."); explanation="Sine is the ratio of the opposite side to the hypotenuse."; }
            default -> throw new IllegalArgumentException("Unsupported topic");
        }
        var options=new LinkedHashSet<String>(); options.add(""+answer);
        while(options.size()<4) options.add(""+(answer+rng.nextInt(17)-8));
        var shuffled=new ArrayList<>(options); Collections.shuffle(shuffled,rng);
        try {
            String fingerprint=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.toLowerCase(Locale.ROOT).replaceAll("\\s+"," ").getBytes(StandardCharsets.UTF_8)));
            var q=new Question(topic,difficulty,text,List.copyOf(shuffled),""+answer,formula,steps,explanation,fingerprint);
            validate(q); return q;
        } catch(java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    private boolean isSchoolTopic(String topic) {
        return List.of("evs-","science-","english-","social-","computer-","physics-","chemistry-","biology-","economics-").stream().anyMatch(topic::startsWith);
    }
    private Question generateSchoolQuestion(String topic,int difficulty,Integer classLevel,Random rng) {
        Fact fact=switch(topic) {
            case "evs-living" -> pick(rng,
                fact("Which plant part usually absorbs water from the soil?","Roots",List.of("Flowers","Fruits","Seeds"),"Roots take up water and minerals from the soil."),
                fact("Which of these is a living thing?","A growing plant",List.of("A stone","A glass cup","A toy car"),"Living things grow and carry out life processes."),
                fact("Which part of a plant mainly makes food using sunlight?","Leaves",List.of("Roots","Flowers","Seeds"),"Leaves contain chlorophyll, which helps plants use light to make food."),
                fact("What do most seeds need to begin germinating?","Water, air, and suitable warmth",List.of("Paint, soil, and darkness","Only bright light","Salt water and ice"),"Many seeds need water, oxygen, and a suitable temperature to germinate."),
                fact("Which animal is a herbivore?","A deer",List.of("A tiger","A hawk","A crocodile"),"Herbivores eat plants; deer commonly feed on vegetation."));
            case "evs-water" -> pick(rng,
                fact("What happens to liquid water when it freezes?","It becomes ice",List.of("It becomes soil","It becomes oxygen","It disappears"),"Water changes from a liquid to a solid when it freezes."),
                fact("What is the process called when liquid water changes into water vapour?","Evaporation",List.of("Condensation","Freezing","Melting"),"Heat can change liquid water into water vapour through evaporation."),
                fact("Which is a sensible way to save water at home?","Turn off the tap while brushing",List.of("Leave taps running","Wash one cup under a running hose","Throw clean water away"),"Turning off unused taps reduces unnecessary water use."),
                fact("What is the main purpose of a dam reservoir?","To store water",List.of("To make clouds","To stop Earth's rotation","To create soil"),"Reservoirs store water for uses such as supply, irrigation, and power generation."),
                fact("Which source is replenished naturally and can be renewed?","Rainwater",List.of("A coal seam","A plastic bottle","A metal can"),"Rain is part of the water cycle and can replenish water sources."));
            case "evs-earth" -> pick(rng,
                fact("What causes day and night on Earth?","Earth rotating on its axis",List.of("The Moon orbiting Earth","Earth changing its shape","Clouds moving across the sky"),"Earth's rotation makes different parts face toward or away from the Sun."),
                fact("Which of these is a natural source of light?","The Sun",List.of("A mirror","A book","A window"),"The Sun produces its own light; mirrors only reflect light."),
                fact("Which action helps reduce litter?","Put waste in the correct bin",List.of("Drop wrappers on the ground","Throw rubbish into a stream","Leave waste in a park"),"Using bins helps keep shared spaces clean."),
                fact("Which object is a natural satellite of Earth?","The Moon",List.of("Mars","The Sun","Venus"),"The Moon is Earth's natural satellite."),
                fact("Which material can usually be recycled into new paper products?","Clean paper",List.of("Food scraps mixed with oil","Used motor oil","Broken batteries"),"Clean paper can be collected and recycled into new paper products."));
            case "evs-health" -> pick(rng,
                fact("What is a good way to reduce the spread of germs before eating?","Wash your hands with soap",List.of("Touch your face","Share an unwashed towel","Wipe your hands on a desk"),"Soap and water help remove germs from hands."),
                fact("Which food group is an important source of protein?","Pulses",List.of("Soft drinks","Table salt","Cooking oil"),"Pulses such as lentils are common sources of protein."),
                fact("Why should drinking water be kept in a clean, covered container?","To reduce contamination",List.of("To make it salty","To remove all minerals","To make it evaporate faster"),"A clean cover helps keep dust and other contaminants out."),
                fact("Which nutrient is the body's main quick energy source?","Carbohydrates",List.of("Vitamins","Water","Minerals"),"Carbohydrates are an important energy source in the diet."),
                fact("What should you do before treating a small cut?","Clean your hands",List.of("Touch it with dirty hands","Cover it with soil","Ignore all bleeding"),"Clean hands reduce the chance of introducing germs while giving basic first aid."));
            case "science-life" -> pick(rng,
                fact("What is the basic structural unit of living organisms?","The cell",List.of("The molecule","The organ system","The ecosystem"),"Cells are the basic structural and functional units of living organisms."),
                fact("Which process lets green plants make food using light?","Photosynthesis",List.of("Respiration","Digestion","Evaporation"),"Photosynthesis uses light energy to make sugars from carbon dioxide and water."),
                fact("Which cell structure contains most of a cell's genetic material?","The nucleus",List.of("The cell wall","The vacuole","The cytoplasm"),"In most plant and animal cells, the nucleus contains most of the genetic material."),
                fact("Which organisms make their own food and often form the start of a food chain?","Producers",List.of("Primary consumers","Decomposers only","Scavengers only"),"Producers such as green plants make organic food and support food chains."),
                fact("What is respiration in cells mainly used to do?","Release usable energy from food molecules",List.of("Make sunlight","Turn cells into minerals","Stop all chemical reactions"),"Cellular respiration releases energy from food molecules for cell activities."));
            case "science-matter" -> pick(rng,
                fact("What is the change from a solid to a liquid called?","Melting",List.of("Freezing","Condensation","Sublimation"),"A solid melts when it changes into a liquid."),
                fact("Which state of matter has a fixed volume but takes the shape of its container?","Liquid",List.of("Solid","Gas","Vacuum"),"A liquid has a fixed volume but flows to fit its container."),
                fact("What is the smallest unit of an element that retains its chemical identity?","An atom",List.of("A cell","A tissue","A mixture"),"An atom is the smallest unit of an element that retains its chemical properties."),
                fact("Which method can separate an insoluble solid from a liquid?","Filtration",List.of("Magnetisation in every case","Melting the liquid","Condensing the solid"),"Filtration separates an insoluble solid from a liquid using a filter."),
                fact("What is a pure substance made from two or more elements chemically combined?","A compound",List.of("A solution only","A suspension","A physical mixture"),"A compound contains elements chemically combined in fixed proportions."));
            case "science-force" -> pick(rng,
                fact("What is friction?","A force that opposes relative motion between surfaces",List.of("A force that creates mass","A type of light","A measure of temperature"),"Friction acts between surfaces and opposes their relative motion."),
                fact("What force pulls objects toward Earth?","Gravity",List.of("Magnetism","Friction","Buoyancy only"),"Gravity attracts objects with mass toward Earth."),
                fact("What happens to an object's speed if it travels 60 metres in 10 seconds at a constant rate?","6 metres per second",List.of("5 metres per second","10 metres per second","600 metres per second"),"Speed equals distance divided by time: 60 ÷ 10 = 6 metres per second."),
                fact("Which force opposes motion between two surfaces in contact?","Friction",List.of("Gravity","Electrostatic force only","Upthrust in every case"),"Friction opposes relative movement between surfaces in contact."),
                fact("What is the SI unit of force?","Newton",List.of("Joule","Watt","Pascal"),"Force is measured in newtons."));
            case "science-energy" -> pick(rng,
                fact("What type of energy does a moving object have?","Kinetic energy",List.of("Chemical energy only","Nuclear energy only","Rest energy"),"Kinetic energy is associated with motion."),
                fact("What happens when light reflects from a plane mirror?","It bounces off the surface",List.of("It turns into sound","It stops existing","It becomes matter"),"Reflection is the bouncing back of light from a surface."),
                fact("Which form of energy is stored in a stretched spring?","Elastic potential energy",List.of("Sound energy","Electrical current","Nuclear energy"),"A deformed spring stores elastic potential energy."),
                fact("What is the bending of light as it passes between materials called?","Refraction",List.of("Reflection","Conduction","Evaporation"),"Refraction is the change in direction of light as its speed changes between media."),
                fact("Which type of lens converges parallel rays of light?","Convex lens",List.of("Concave lens","Plane mirror","Opaque screen"),"A convex lens can converge parallel rays toward a focal point."));
            case "science-electricity" -> pick(rng,
                fact("Which material is a good electrical conductor?","Copper",List.of("Rubber","Dry wood","Plastic"),"Copper allows electric charge to flow and is widely used in wires."),
                fact("What must be true for current to flow in a simple circuit?","The circuit must be closed",List.of("The switch must be open","The wire must be cut","The battery must be removed"),"A closed conducting path lets current flow through a simple circuit."),
                fact("What does an ammeter measure?","Electric current",List.of("Temperature","Mass","Time"),"An ammeter measures electric current."),
                fact("Which component can open or close a simple circuit?","A switch",List.of("A ruler","A lens","A magnet in every circuit"),"A switch controls whether the conducting path is open or closed."),
                fact("What is the SI unit of potential difference?","Volt",List.of("Ampere","Ohm","Coulomb"),"Potential difference is measured in volts."));
            case "science-environment" -> pick(rng,
                fact("Which organisms break down dead material and return nutrients to ecosystems?","Decomposers",List.of("Producers","Only predators","Pollinators only"),"Decomposers break down dead organisms and recycle nutrients."),
                fact("Which gas do plants take in during photosynthesis?","Carbon dioxide",List.of("Oxygen","Hydrogen","Helium"),"Plants use carbon dioxide and water to make sugars during photosynthesis."),
                fact("What does biodiversity describe?","The variety of living organisms",List.of("The speed of wind","The depth of one lake","The number of buildings"),"Biodiversity is the variety of life in an area or on Earth."),
                fact("Which gas is a major contributor to human-caused climate change?","Carbon dioxide",List.of("Neon","Argon","Helium"),"Carbon dioxide is a long-lived greenhouse gas whose human-caused emissions contribute to warming."),
                fact("What is a food web?","A network of connected food chains",List.of("A single plant root","A weather map","A list of minerals"),"A food web shows multiple feeding relationships within an ecosystem."));
            case "english-grammar" -> pick(rng,
                fact("In the sentence “Mira carefully opened the box,” which word is an adverb?","carefully",List.of("Mira","opened","box"),"“Carefully” describes how Mira opened the box, so it is an adverb."),
                fact("Which word is a proper noun?","Jaipur",List.of("city","river","school"),"“Jaipur” is the specific name of a place, so it is a proper noun."),
                fact("In “The bright star shone,” which word is the adjective?","bright",List.of("The","star","shone"),"“Bright” describes the noun “star,” so it is an adjective."),
                fact("Which word is the abstract noun in “Honesty matters”?","Honesty",List.of("matters","the","is"),"“Honesty” names a quality, so it is an abstract noun."),
                fact("Choose the correct pronoun: “Ravi and ___ are classmates.”","I",List.of("me","mine","myself"),"Use the subject pronoun “I” as part of the compound subject."));
            case "english-tenses" -> pick(rng,
                fact("Choose the correct verb: “She ___ to school every day.”","goes",List.of("go","went","going"),"The singular subject “she” takes “goes” for a habitual action in the present simple."),
                fact("Choose the correct verb: “They ___ the match yesterday.”","watched",List.of("watch","watches","watching"),"“Yesterday” signals a completed past action, so use “watched.”"),
                fact("Choose the correct verb: “I ___ my homework right now.”","am doing",List.of("did","do","will did"),"“Right now” describes an action in progress: “am doing.”"),
                fact("Choose the correct verb: “By next week, she ___ the project.”","will have finished",List.of("finishes yesterday","has finish","will finishing"),"“By next week” points to completion before a future time, so use the future perfect."),
                fact("Choose the correct verb: “We ___ dinner when the lights went out.”","were eating",List.of("eat","will eat","have eaten"),"The past continuous describes an action in progress when another past event happened."));
            case "english-vocabulary" -> pick(rng,
                fact("Which word is closest in meaning to “rapid”?","Quick",List.of("Distant","Quiet","Careful"),"“Rapid” means fast or quick."),
                fact("Which word is the opposite of “ancient”?","Modern",List.of("Old","Historic","Early"),"“Modern” is the opposite of “ancient.”"),
                fact("Which word is closest in meaning to “assist”?","Help",List.of("Hinder","Hide","Wait"),"“Assist” means to help."),
                fact("What does “reluctant” mean?","Unwilling or hesitant",List.of("Eager","Certain","Careless"),"A reluctant person is unwilling or hesitant to do something."),
                fact("Which word is a synonym of “enormous”?","Huge",List.of("Tiny","Narrow","Ordinary"),"“Enormous” means very large; “huge” is a synonym."));
            case "english-sentences" -> pick(rng,
                fact("Which sentence has a complete subject and predicate?","The little bird sang.",List.of("Because the bird.","Sang in the morning because.","The little."),"A complete sentence expresses a complete thought with a subject and a predicate."),
                fact("Which is an interrogative sentence?","Where is the library?",List.of("The library is nearby.","Please visit the library.","What a lovely library."),"An interrogative sentence asks a question."),
                fact("Which sentence uses subject–verb agreement correctly?","The children are ready.",List.of("The children is ready.","The children am ready.","The children was ready now."),"The plural subject “children” agrees with “are.”"),
                fact("Which sentence is in the passive voice?","The window was opened by Neha.",List.of("Neha opened the window.","Neha is opening the window.","Neha will open the window."),"In passive voice, the subject receives the action: the window was opened."),
                fact("Which sentence is a command?","Please close the door.",List.of("Did you close the door?","The door is closed.","What a heavy door!"),"A command or imperative sentence gives a direction or request."));
            case "english-punctuation" -> pick(rng,
                fact("Which punctuation mark normally ends a direct question?","Question mark (?)",List.of("Comma (,)","Apostrophe (')","Colon (:)"),"A direct question normally ends with a question mark."),
                fact("Which punctuation correctly shows that a book belongs to Riya?","Riya's book",List.of("Riyas book?","Riya,s book","Riya: book"),"An apostrophe can mark possession: Riya's book."),
                fact("Which mark separates items in a simple list?","Comma",List.of("Apostrophe","Question mark","Hyphen only"),"Commas separate items in a list."),
                fact("Which punctuation correctly shows a short pause between two clauses?","Comma",List.of("Apostrophe","Question mark","Hyphen"),"A comma can mark a pause or separate clauses, depending on sentence structure."),
                fact("Which punctuation mark is used at the end of an exclamation?","Exclamation mark (!)",List.of("Full stop only","Comma (,)","Apostrophe (')"),"An exclamation mark can show strong feeling or emphasis."));
            case "social-history" -> pick(rng,
                fact("Which ancient civilisation is associated with cities such as Harappa and Mohenjo-daro?","Indus Valley Civilisation",List.of("Roman Empire","Inca Empire","Maya civilisation"),"Harappa and Mohenjo-daro were major cities of the Indus Valley Civilisation."),
                fact("Who was the Mauryan ruler associated with the Kalinga War and later promotion of dhamma?","Ashoka",List.of("Akbar","Chandragupta II","Samudragupta"),"Ashoka's inscriptions describe his response to the Kalinga War and his promotion of dhamma."),
                fact("In which year did India become independent from British rule?","1947",List.of("1857","1905","1950"),"India became independent on 15 August 1947."),
                fact("What is a primary historical source?","Evidence created during the period being studied",List.of("A modern fictional story","A guess without evidence","A present-day weather forecast"),"Letters, objects, and records from the time are primary sources."),
                fact("Which event began in 1857 as a major uprising against East India Company rule?","The Indian Rebellion of 1857",List.of("The Dandi March","The Quit India Movement","The Non-Cooperation Movement"),"The 1857 uprising challenged East India Company rule across parts of India."));
            case "social-geography" -> pick(rng,
                fact("What does the Equator divide Earth into?","Northern and Southern Hemispheres",List.of("Eastern and Western Hemispheres","The continents and oceans","The inner and outer core"),"The Equator is an imaginary line that divides Earth into the Northern and Southern Hemispheres."),
                fact("Which is the largest ocean on Earth?","Pacific Ocean",List.of("Indian Ocean","Arctic Ocean","Southern Ocean"),"The Pacific Ocean is the largest ocean by area."),
                fact("Which instrument is commonly used to find direction?","Compass",List.of("Thermometer","Rain gauge","Barometer"),"A compass needle aligns approximately with Earth's magnetic field and helps indicate direction."),
                fact("What do contour lines on a topographic map show?","Places of equal elevation",List.of("Places with equal rainfall only","Political parties","Road traffic speed"),"Each contour line joins points at the same elevation."),
                fact("Which imaginary line is at 0° longitude?","Prime Meridian",List.of("Equator","Tropic of Cancer","International Date Line"),"The Prime Meridian is the reference line for 0° longitude."));
            case "social-civics" -> pick(rng,
                fact("In a democracy, how do citizens commonly choose their representatives?","By voting in elections",List.of("By inheriting the office","By drawing lots in every case","By appointment from another country"),"Elections allow citizens to choose representatives in a democracy."),
                fact("What is the main purpose of a constitution?","To set fundamental rules and principles for governing a country",List.of("To list every private conversation","To replace all laws with customs","To describe only a country's geography"),"A constitution establishes the basic framework, powers, and principles of government."),
                fact("Which branch of government generally makes laws?","The legislature",List.of("The judiciary","The weather service","The election campaign"),"The legislature is responsible for making laws; exact structures differ between countries."),
                fact("What is the main role of an independent judiciary?","Interpret laws and resolve disputes",List.of("Print currency","Conduct every election","Make all household decisions"),"Courts interpret and apply laws and resolve legal disputes."),
                fact("Why are fundamental rights included in a constitution?","To protect basic freedoms and equality",List.of("To guarantee every person the same job","To remove all civic duties","To set daily weather rules"),"Constitutional rights protect basic freedoms and principles such as equality."));
            case "social-economics" -> pick(rng,
                fact("What does scarcity mean in economics?","Resources are limited compared with people's wants",List.of("All goods are free","People have no needs","Resources are unlimited"),"Scarcity exists because resources are limited while wants can exceed them."),
                fact("Which activity belongs to the primary sector?","Growing crops",List.of("Making a car in a factory","Providing banking services","Selling a phone in a shop"),"The primary sector obtains or produces raw materials, including through farming."),
                fact("What is a budget?","A plan for income and spending",List.of("A type of natural resource","A list of historical dates","A measure of temperature"),"A budget plans how available income will be allocated to spending and saving."),
                fact("What is the main purpose of saving?","To set aside income for future needs or goals",List.of("To increase today's spending automatically","To remove all financial risk","To avoid making a budget"),"Saving sets aside some income for future needs, emergencies, or goals."),
                fact("Which is an example of a public good?","Street lighting",List.of("A personal notebook","A private meal","A pair of shoes"),"Public goods such as street lighting are commonly provided for shared community benefit."));
            case "computer-basics" -> pick(rng,
                fact("Which computer component executes instructions and processes data?","CPU",List.of("Monitor","Keyboard","Speaker"),"The CPU processes instructions and data."),
                fact("Which is an input device?","Keyboard",List.of("Monitor","Speaker","Projector"),"A keyboard sends user input to a computer."),
                fact("Which type of memory usually loses its contents when power is turned off?","RAM",List.of("ROM","SSD storage","DVD"),"RAM is volatile working memory; its contents are normally lost when power is removed."),
                fact("Which device displays visual output from a computer?","Monitor",List.of("Keyboard","Microphone","Scanner"),"A monitor is an output device that displays text, images, and video."),
                fact("Which component stores files even when the computer is switched off?","SSD or hard drive",List.of("RAM","CPU cache only","A fan"),"Persistent storage such as an SSD or hard drive keeps files without power."));
            case "computer-coding" -> pick(rng,
                fact("What is an algorithm?","A finite sequence of steps for solving a problem",List.of("A computer screen","A type of battery","A network cable"),"An algorithm is an ordered procedure for solving a problem or completing a task."),
                fact("What does a loop do in a program?","Repeats a set of instructions",List.of("Deletes every variable","Turns off the computer","Changes a monitor into a printer"),"A loop repeats instructions while a condition or count allows it."),
                fact("What is debugging?","Finding and fixing errors in a program",List.of("Designing a keyboard","Compressing a photo","Connecting a power cable"),"Debugging is the process of locating and correcting program errors."),
                fact("In a flowchart, what does a diamond usually represent?","A decision or condition",List.of("The program's start only","A printed page","A data storage device"),"A diamond is commonly used for a decision with branches such as yes/no."),
                fact("What is a variable in a program?","A named place for a value that can be used or changed",List.of("A type of monitor","A fixed network cable","A printed instruction sheet"),"A variable is a named storage location for data used by a program."));
            case "computer-data" -> pick(rng,
                fact("How many bits are in one byte?","8",List.of("2","4","16"),"One byte contains eight bits."),
                fact("What does a computer network allow connected devices to do?","Exchange data and share resources",List.of("Change their physical size","Work without any power","Avoid using software"),"Networks connect devices so they can communicate and share resources."),
                fact("Which number system uses only 0 and 1?","Binary",List.of("Decimal","Roman numerals","Hexadecimal only"),"Binary is a base-two number system that uses 0 and 1."),
                fact("What does a file extension such as .pdf usually indicate?","The file format or type",List.of("The file owner's password","The computer's processor speed","The file's physical location only"),"A file extension commonly indicates the format and associated application."),
                fact("How many different values can one bit represent?","Two: 0 and 1",List.of("One only","Eight","Ten"),"A bit is binary and can hold either 0 or 1."));
            case "computer-safety" -> pick(rng,
                fact("Which is the safest response to a message asking for your password?","Do not share it and report the message",List.of("Send it immediately","Post it publicly","Reuse it as your username"),"Passwords should remain private; suspicious requests should be reported."),
                fact("What is phishing?","A deceptive attempt to steal information",List.of("A way to cool a computer","A type of spreadsheet formula","A method of printing"),"Phishing uses deceptive messages or pages to trick people into revealing information."),
                fact("Which password is generally harder to guess?","A long, unique passphrase",List.of("123456","Your first name","The word password"),"Long, unique passwords or passphrases are harder to guess than common choices."),
                fact("Why should software security updates be installed?","They can fix known vulnerabilities",List.of("They make every password public","They remove the need for backups","They guarantee a device cannot fail"),"Updates often repair security weaknesses discovered in earlier versions."),
                fact("What should you do before opening an unexpected email attachment?","Verify the sender and whether it is expected",List.of("Open it immediately","Forward it to everyone","Disable security software"),"Unexpected attachments can be malicious, so verify them before opening."));
            case "physics-motion" -> pick(rng,
                fact("A car travels 120 metres in 10 seconds at constant speed. What is its speed?","12 m/s",List.of("10 m/s","110 m/s","1,200 m/s"),"Speed = distance ÷ time = 120 ÷ 10 = 12 m/s.","v = d/t"),
                fact("What is the SI unit of acceleration?","m/s²",List.of("m/s","N·m","kg/m³"),"Acceleration is the change in velocity per unit time and is measured in metres per second squared.","a = Δv/Δt"),
                fact("If the net force on an object is zero, what is its acceleration?","Zero",List.of("Always 9.8 m/s²","Always increasing","Impossible to determine its acceleration"),"Newton's second law gives a = F/m; zero net force means zero acceleration.","F = ma"),
                fact("A runner covers 200 m in 25 s. What is the runner's average speed?","8 m/s",List.of("5 m/s","25 m/s","225 m/s"),"Average speed = total distance ÷ total time = 200 ÷ 25 = 8 m/s.","v = d/t"),
                fact("A velocity changes from 4 m/s to 16 m/s in 3 s. What is the acceleration?","4 m/s²",List.of("3 m/s²","12 m/s²","20 m/s²"),"Acceleration = change in velocity ÷ time = (16 − 4) ÷ 3 = 4 m/s².","a = Δv/Δt"));
            case "physics-energy" -> pick(rng,
                fact("What is the SI unit of work and energy?","Joule",List.of("Watt","Newton per metre","Pascal"),"Work and energy are measured in joules.","W = Fd"),
                fact("A 2 kg object moves at 3 m/s. What is its kinetic energy?","9 J",List.of("6 J","18 J","3 J"),"Kinetic energy = ½mv² = ½ × 2 × 3² = 9 J.","KE = ½mv²"),
                fact("What is power?","The rate at which work is done or energy is transferred",List.of("The amount of matter in an object","The distance travelled per unit time","The force per unit area"),"Power measures the rate of doing work or transferring energy.","P = W/t"),
                fact("A 10 N force moves an object 4 m in its direction. How much work is done?","40 J",List.of("2.5 J","14 J","400 J"),"Work = force × displacement in the force direction = 10 × 4 = 40 J.","W = Fd"),
                fact("A 5 kg object is lifted 2 m. Using g = 10 m/s², what gain in gravitational potential energy does it have?","100 J",List.of("10 J","25 J","250 J"),"ΔGPE = mgh = 5 × 10 × 2 = 100 J.","ΔGPE = mgh"));
            case "physics-electricity" -> pick(rng,
                fact("What is the SI unit of electric current?","Ampere",List.of("Volt","Ohm","Coulomb per metre"),"Electric current is measured in amperes.","I = Q/t"),
                fact("A 6 Ω resistor is connected to 12 V. What current flows?","2 A",List.of("0.5 A","6 A","72 A"),"Ohm's law gives I = V/R = 12/6 = 2 A.","V = IR"),
                fact("What is the SI unit of electrical resistance?","Ohm",List.of("Ampere","Watt-hour","Tesla"),"Electrical resistance is measured in ohms.","R = V/I"),
                fact("A 3 A current flows through a 4 Ω resistor. What is the potential difference?","12 V",List.of("1.3 V","7 V","24 V"),"Ohm's law gives V = IR = 3 × 4 = 12 V.","V = IR"),
                fact("How much charge passes a point in 5 s if the current is 2 A?","10 C",List.of("2.5 C","7 C","25 C"),"Charge = current × time = 2 × 5 = 10 C.","Q = It"));
            case "physics-waves" -> pick(rng,
                fact("What is the relationship between wave speed, frequency, and wavelength?","v = fλ",List.of("v = f/λ","v = λ/f","v = f + λ"),"Wave speed equals frequency multiplied by wavelength.","v = fλ"),
                fact("Which type of wave can travel through a vacuum?","Electromagnetic wave",List.of("Sound wave","Water wave","Seismic S-wave"),"Electromagnetic waves do not require a material medium."),
                fact("What happens to the frequency of a wave if its period increases?","The frequency decreases",List.of("The frequency increases","The frequency stays infinite","Frequency becomes wavelength"),"Frequency and period are inversely related: f = 1/T.","f = 1/T"),
                fact("A wave has frequency 5 Hz and wavelength 2 m. What is its speed?","10 m/s",List.of("2.5 m/s","7 m/s","20 m/s"),"Wave speed = frequency × wavelength = 5 × 2 = 10 m/s.","v = fλ"),
                fact("What is the period of a wave with frequency 4 Hz?","0.25 s",List.of("2 s","4 s","16 s"),"Period = 1 ÷ frequency = 1 ÷ 4 = 0.25 seconds.","T = 1/f"));
            case "chemistry-atoms" -> pick(rng,
                fact("What does the atomic number of an element equal?","The number of protons in its nucleus",List.of("The number of neutrons only","The number of shells","The total number of molecules"),"An element's atomic number is the number of protons in its nucleus."),
                fact("Where are electrons found in an atom?","In regions around the nucleus",List.of("Only inside protons","Only between atoms","Inside the neutron's nucleus"),"Electrons occupy regions around the atomic nucleus."),
                fact("What do elements in the same group of the periodic table generally share?","A similar number of outer-shell electrons",List.of("The same number of neutrons in every isotope","The same atomic mass","The same number of electron shells"),"Elements in a group often have similar chemical properties because of their valence-electron patterns."),
                fact("How many protons does a neutral carbon atom have?","6",List.of("4","8","12"),"Carbon's atomic number is 6, which equals its number of protons."),
                fact("What is an isotope?","Atoms of the same element with different numbers of neutrons",List.of("Atoms with different proton counts but the same element","A mixture of two compounds","An atom with no electrons in every case"),"Isotopes share the same proton number but differ in neutron number."));
            case "chemistry-reactions" -> pick(rng,
                fact("In a chemical equation, what are reactants?","Starting substances that undergo a chemical change",List.of("Only the final products","The reaction container","A unit of temperature"),"Reactants are the substances present before a chemical reaction produces products."),
                fact("What is formed when an acid reacts with a base in a neutralisation reaction?","Salt and water",List.of("Only oxygen","A metal and hydrogen in every case","Sugar and carbon dioxide"),"Acid–base neutralisation commonly forms a salt and water."),
                fact("What does a balanced chemical equation conserve?","The number of atoms of each element",List.of("The number of molecules on both sides always","The temperature","The colour of every substance"),"Balancing ensures each element has the same number of atoms on both sides."),
                fact("What gas is produced when many metals react with dilute acids?","Hydrogen",List.of("Oxygen","Nitrogen","Carbon dioxide in every case"),"A common metal–acid reaction produces a salt and hydrogen gas."),
                fact("In the reaction 2H₂ + O₂ → 2H₂O, which substance is a product?","Water (H₂O)",List.of("Hydrogen (H₂)","Oxygen (O₂)","Both reactants only"),"Products appear on the right side of a chemical equation; here water is formed."));
            case "chemistry-acids" -> pick(rng,
                fact("At room conditions, what does a pH below 7 usually indicate?","An acidic solution",List.of("A basic solution","A neutral solution","A pure metal"),"On the common pH scale, values below 7 are acidic."),
                fact("What colour does blue litmus typically turn in an acid?","Red",List.of("Green","Blue","Black"),"Acids turn blue litmus paper red."),
                fact("Which ion is associated with acidic aqueous solutions?","H⁺",List.of("OH⁻ only","Na⁺ only","Cl⁻ only"),"Acidic aqueous solutions have an increased concentration of hydrogen ions."),
                fact("What is the pH of a neutral aqueous solution at about 25°C?","7",List.of("1","5","14"),"At about 25°C, a neutral aqueous solution has pH 7."),
                fact("What happens to red litmus paper in a basic solution?","It turns blue",List.of("It turns black","It stays red in every case","It dissolves immediately"),"Bases turn red litmus paper blue."));
            case "chemistry-bonding" -> pick(rng,
                fact("What happens in a covalent bond?","Atoms share electron pairs",List.of("Atoms share protons","Nuclei merge","Neutrons move between shells"),"Covalent bonding involves sharing pairs of electrons."),
                fact("What is a molecule?","Two or more atoms chemically bonded together",List.of("A single proton","A mixture that cannot be separated","A unit of electric current"),"A molecule consists of atoms held together by chemical bonds."),
                fact("Which formula represents water?","H₂O",List.of("CO₂","O₂","NaCl"),"A water molecule contains two hydrogen atoms and one oxygen atom."),
                fact("What type of bond forms when oppositely charged ions attract?","Ionic bond",List.of("Covalent bond","Metallic bond only","Hydrogen bond in every case"),"Ionic bonding is the electrostatic attraction between oppositely charged ions."),
                fact("What is the usual charge on a sodium ion, Na⁺?","+1",List.of("−1","+2","0"),"A sodium atom loses one electron to form Na⁺, giving it a +1 charge."));
            case "biology-cells" -> pick(rng,
                fact("Which molecule carries hereditary information in most living organisms?","DNA",List.of("Starch","Cellulose","Haemoglobin only"),"DNA stores hereditary information in most living organisms."),
                fact("Which structure is present in plant cells and helps support the cell?","Cell wall",List.of("Only a flagellum","A bone shell","A nerve ending"),"Plant cells have a cellulose cell wall outside the cell membrane."),
                fact("What is a gene?","A section of DNA associated with a functional product or trait",List.of("A whole ecosystem","A type of blood cell","A mineral nutrient"),"Genes are regions of DNA that contribute to inherited characteristics."),
                fact("Which organelle releases much of the usable energy during aerobic respiration?","Mitochondrion",List.of("Ribosome","Cell wall","Chloroplast only"),"Mitochondria are major sites of aerobic respiration in eukaryotic cells."),
                fact("What is the main function of a cell membrane?","To regulate movement of substances into and out of the cell",List.of("To make every cell's food","To store all genetic information","To pump blood"),"The cell membrane controls exchange between the cell and its surroundings."));
            case "biology-plants" -> pick(rng,
                fact("Which tissue transports water upward through a plant?","Xylem",List.of("Phloem","Epidermis only","Stomata"),"Xylem transports water and dissolved minerals from roots through the plant."),
                fact("Which pigment absorbs light for photosynthesis?","Chlorophyll",List.of("Haemoglobin","Melanin","Keratin"),"Chlorophyll absorbs light energy used in photosynthesis."),
                fact("Which plant structure usually takes in carbon dioxide for photosynthesis?","Stomata",List.of("Root hairs","Petals only","Seed coats"),"Stomata are pores that allow gas exchange in leaves."),
                fact("Which plant tissue transports sugars from leaves to other parts?","Phloem",List.of("Xylem","Stomata","Cortex only"),"Phloem transports sugars and other organic substances around the plant."),
                fact("Where does most photosynthesis take place in a typical leaf?","In chloroplasts of leaf cells",List.of("Inside root hairs","Only in the xylem","Inside the seed coat"),"Chloroplasts contain chlorophyll and are the main sites of photosynthesis."));
            case "biology-body" -> pick(rng,
                fact("Which organ pumps blood around the human body?","Heart",List.of("Liver","Stomach","Lung"),"The heart pumps blood through the circulatory system."),
                fact("Where does most gas exchange occur in human lungs?","Alveoli",List.of("The oesophagus","The trachea only","The diaphragm"),"Alveoli are tiny air sacs where oxygen and carbon dioxide are exchanged."),
                fact("Which blood cells mainly help defend the body against infection?","White blood cells",List.of("Red blood cells","Platelets only","Bone cells"),"White blood cells are part of the immune system."),
                fact("Which blood component helps form clots to reduce bleeding?","Platelets",List.of("Red blood cells","Plasma glucose","Neurons"),"Platelets help the blood clot at sites of injury."),
                fact("Which organ absorbs most digested nutrients from the small intestine?","The small intestine",List.of("The oesophagus","The trachea","The urinary bladder"),"The lining of the small intestine absorbs most nutrients into the body."));
            case "biology-ecology" -> pick(rng,
                fact("What is an ecosystem?","Organisms interacting with one another and their physical environment",List.of("One animal by itself","Only the weather","A list of species with no environment"),"An ecosystem includes interacting organisms and their physical surroundings."),
                fact("In a food chain, what is a producer usually able to do?","Make its own food",List.of("Feed only on decomposers","Create sunlight","Turn consumers into minerals"),"Producers, such as plants, make organic food using an energy source."),
                fact("What does conservation aim to do?","Protect species, habitats, and natural resources",List.of("Use every resource immediately","Remove all habitats","Prevent all organisms from reproducing"),"Conservation protects biodiversity and natural resources for the future."),
                fact("What is a food web?","A network of connected food chains",List.of("A single organism's life cycle","A weather pattern","A list of rocks"),"A food web connects multiple food chains in an ecosystem."),
                fact("What is the role of decomposers in an ecosystem?","Break down dead material and recycle nutrients",List.of("Produce sunlight","Stop nutrient cycling","Make all consumers extinct"),"Decomposers return nutrients from dead matter to the ecosystem."));
            case "economics-markets" -> pick(rng,
                fact("What is a market?","A system where buyers and sellers exchange goods or services",List.of("Only a public park","A government office in every case","A type of currency"),"A market brings buyers and sellers together for exchange."),
                fact("What is opportunity cost?","The next best alternative given up when a choice is made",List.of("The total number of choices","A tax paid on every purchase","The money printed by a bank"),"Opportunity cost is the value of the next best alternative forgone."),
                fact("Why do people make economic choices?","Resources are limited and choices have trade-offs",List.of("Every resource is unlimited","Choices have no consequences","Prices never change"),"Scarcity means people must choose how to use limited resources."),
                fact("What is a consumer in a market?","A person or organisation that buys goods or services",List.of("Only a factory machine","A law-making body","A natural resource"),"Consumers purchase or use goods and services."),
                fact("If a buyer chooses one option and gives up the next-best option, what is that foregone option called?","The opportunity cost",List.of("The market price only","The total supply","A public good"),"Opportunity cost is the value of the next-best alternative forgone."));
            case "economics-money" -> pick(rng,
                fact("Which is a common function of money?","A medium of exchange",List.of("A natural habitat","A unit of temperature","A type of machine"),"Money can serve as a medium of exchange, a store of value, and a unit of account."),
                fact("What does a bank deposit account generally let a customer do?","Store money and make permitted transactions",List.of("Create unlimited money personally","Avoid all financial records","Guarantee every investment profit"),"Deposit accounts provide a place to hold funds and make transactions under account terms."),
                fact("What is saving?","Income not spent on current consumption",List.of("Every purchase made today","A type of tax","A company's total sales only"),"Saving is the portion of income not used for current spending."),
                fact("Which function of money lets people compare the prices of different goods?","A unit of account",List.of("A store of weather","A production input only","A public service"),"As a unit of account, money provides a common way to express and compare prices."),
                fact("What is interest on a savings deposit generally?","A payment for allowing the bank to use the deposited funds",List.of("A guaranteed prize unrelated to the account","A tax on every withdrawal","The original deposit itself"),"Interest is the return paid on a deposit according to the account's terms."));
            case "economics-demand" -> pick(rng,
                fact("In economics, what does demand describe?","Consumers' willingness and ability to buy at different prices",List.of("Only the quantity a factory can produce","A country's land area","The number of shops in a city"),"Demand reflects willingness and ability to purchase at given prices."),
                fact("If price rises and other factors stay similar, what often happens to quantity demanded?","It falls",List.of("It always doubles","It stays exactly the same","It becomes supply"),"The law of demand describes an inverse relationship between price and quantity demanded, other things equal."),
                fact("What does supply describe?","How much sellers are willing and able to offer at different prices",List.of("Only consumer income","A country's population","The amount of rainfall"),"Supply describes sellers' willingness and ability to offer goods at different prices."),
                fact("If consumers' income rises and the good is normal, what usually happens to its demand?","It increases",List.of("It always becomes zero","It cannot change","It becomes supply"),"Demand for a normal good generally rises when consumer income increases, other things equal."),
                fact("What is market equilibrium?","The point where quantity demanded equals quantity supplied",List.of("The point where no one can buy a good","A price set without buyers or sellers","The highest possible production cost"),"At equilibrium, the quantity buyers want equals the quantity sellers offer."));
            case "economics-income" -> pick(rng,
                fact("What does gross domestic product (GDP) measure?","The value of final goods and services produced within a country in a period",List.of("Only household savings","The number of citizens","The value of imported goods only"),"GDP measures production of final goods and services within a country's borders over a period."),
                fact("Why are intermediate goods excluded from GDP when final goods are counted?","To avoid counting the same production more than once",List.of("They have no economic value","They are always imported","They are not bought by firms"),"Excluding intermediate goods avoids double counting their value in final output."),
                fact("What is per-capita income commonly calculated as?","National income divided by population",List.of("Population divided by land area","Exports minus imports only","Tax revenue multiplied by prices"),"Per-capita income is an average found by dividing an income measure by population."),
                fact("If national income is ₹900 billion and population is 300 million, what is income per person?","₹3,000",List.of("₹300","₹2,700","₹27,000"),"Per-capita income = ₹900 billion ÷ 300 million = ₹3,000 per person."),
                fact("Which is counted in GDP using the expenditure approach?","Final spending on goods and services produced domestically",List.of("Every intermediate sale counted repeatedly","Unpaid household chores in every calculation","Only imported products"),"The expenditure approach totals final expenditure on domestic production."));
            default -> throw new IllegalArgumentException("Unsupported curriculum topic");
        };
        var options=new ArrayList<String>();options.add(fact.answer());options.addAll(fact.distractors());Collections.shuffle(options,rng);
        try {
            String fingerprint=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(fact.prompt().toLowerCase(Locale.ROOT).replaceAll("\\s+"," ").getBytes(StandardCharsets.UTF_8)));
            var question=new Question(topic,difficulty,fact.prompt(),List.copyOf(options),fact.answer(),fact.formula(),fact.steps(),fact.explanation(),fingerprint);
            validate(question);return question;
        } catch(java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    private Fact fact(String prompt,String answer,List<String> distractors,String explanation) { return fact(prompt,answer,distractors,"Recall the key idea",List.of("Identify what the question asks.",explanation),explanation); }
    private Fact fact(String prompt,String answer,List<String> distractors,String formula,String explanation) { return fact(prompt,answer,distractors,formula,List.of("Select the relevant rule or definition.",explanation),explanation); }
    private Fact fact(String prompt,String answer,List<String> distractors,String formula,List<String> steps,String explanation) { return new Fact(prompt,answer,distractors,formula,steps,explanation); }
    private Fact pick(Random rng,Fact... facts) { return facts[rng.nextInt(facts.length)]; }
    public void validate(Question q) {
        if(q.text().isBlank() || q.options().size()!=4 || new HashSet<>(q.options()).size()!=4 || !q.options().contains(q.answer()) || q.steps().isEmpty() || q.formula().isBlank()) throw new IllegalArgumentException("Invalid question");
    }
}
