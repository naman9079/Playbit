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
        if(classLevel!=null && (classLevel<6 || classLevel>12)) throw new IllegalArgumentException("Class level");
        double classScale=classLevel==null?1.0:1.0+(classLevel-6)*0.25;
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
            default -> throw new IllegalArgumentException("Unsupported topic");
        }
        var options=new LinkedHashSet<String>(); options.add(""+answer);
        while(options.size()<4) options.add(""+Math.max(0,answer+rng.nextInt(17)-8));
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
