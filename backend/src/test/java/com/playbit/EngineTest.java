package com.playbit;

import java.time.*;
import java.util.*;
import com.playbit.service.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class EngineTest {
    @Test void allGeneratorsProduceUniqueOptionsAndVerifiedArithmetic() {
        var generator=new QuestionGenerationService();
        var fingerprints=new HashSet<String>();
        for(String topic:List.of("fractions","algebra","exponents","percentages","profit-loss","time-work","number-series","directions","coding")) for(int difficulty=1;difficulty<=3;difficulty++) for(int seed=0;seed<200;seed++) {
            var q=generator.generate(topic,difficulty,new Random(seed));generator.validate(q);
            assertEquals(4,new HashSet<>(q.options()).size());assertTrue(q.options().contains(q.answer()));assertFalse(q.steps().isEmpty());assertEquals(64,q.fingerprint().length());fingerprints.add(q.fingerprint());
            var nums=java.util.regex.Pattern.compile("\\d+").matcher(q.text());var n=new ArrayList<Integer>();while(nums.find())n.add(Integer.parseInt(nums.group()));
            int expected=switch(topic){case "fractions"->n.get(0)+n.get(2);case "algebra"->(n.get(2)-n.get(1))/n.get(0);case "exponents"->n.get(1)+n.get(3);case "percentages"->n.get(0)*n.get(1)/100;case "profit-loss"->(n.get(1)-n.get(0))*100/n.get(0);case "time-work"->n.get(1)*n.get(3);case "number-series"->n.get(3)+(n.get(1)-n.get(0));case "directions"->n.get(1);default->Integer.parseInt(q.answer());};
            assertEquals(expected,Integer.parseInt(q.answer()),q.text());
        }
        assertTrue(fingerprints.size()>1500);
    }
    @Test void invalidQuestionsAreRejected() {var gen=new QuestionGenerationService();assertThrows(IllegalArgumentException.class,()->gen.generate("injection",1,new Random()));assertThrows(IllegalArgumentException.class,()->gen.generate("algebra",0,new Random()));assertThrows(IllegalArgumentException.class,()->gen.validate(new QuestionGenerationService.Question("algebra",1,"x",List.of("1","1","2","3"),"1","f",List.of("s"),"e","f")));}
    @Test void scoringUsesServerElapsedTime() {assertEquals(120,MatchService.score(true,0));assertEquals(110,MatchService.score(true,7500));assertEquals(100,MatchService.score(true,14999));assertEquals(0,MatchService.score(false,0));}
    @Test void levelThresholdsMatchSpecification() {assertEquals(List.of(0,100,250,450,700),List.of(XpService.threshold(1),XpService.threshold(2),XpService.threshold(3),XpService.threshold(4),XpService.threshold(5)));assertEquals(1,XpService.level(99));assertEquals(2,XpService.level(100));assertEquals(5,XpService.level(700));}
    @Test void eloAndPlacementAreIndependentAndBalanced() {var r=new RatingService();assertEquals(16,r.delta(1000,1000,1));assertEquals(-16,r.delta(1000,1000,0));assertEquals(0,r.delta(1000,1000,.5));assertTrue(r.delta(800,1200,1)>r.delta(1200,800,1));assertEquals(800,r.diagnostic("beginner",3));assertEquals(1160,r.diagnostic("intermediate",5));assertEquals(1040,r.diagnostic("advanced",1));}
}
