package com.playbit.service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class QuestionGenerationService {
    public record Question(String topic, int difficulty, String text, List<String> options, String answer, String formula, List<String> steps, String explanation, String fingerprint) {}
    public Question generate(String topic,int difficulty,Random rng) {
        return generate(topic,difficulty,null,rng);
    }
    public Question generate(String topic,int difficulty,Integer classLevel,Random rng) {
        if(difficulty<1 || difficulty>3) throw new IllegalArgumentException("Difficulty");
        if(classLevel!=null && (classLevel<1 || classLevel>12)) throw new IllegalArgumentException("Class level");
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
    public void validate(Question q) {
        if(q.text().isBlank() || q.options().size()!=4 || new HashSet<>(q.options()).size()!=4 || !q.options().contains(q.answer()) || q.steps().isEmpty() || q.formula().isBlank()) throw new IllegalArgumentException("Invalid question");
    }
}
