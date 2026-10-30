package com.playbit.service;
import org.springframework.stereotype.Service;
@Service
public class RatingService {
    public int delta(int own,int opponent,double result) { return (int)Math.round(32*(result-1/(1+Math.pow(10,(opponent-own)/400.0)))); }
    public int diagnostic(String proficiency,int correct) { int base=switch(proficiency) { case "advanced" -> 1200; case "intermediate" -> 1000; default -> 800; }; return Math.max(600,Math.min(1400,base+(correct-3)*80)); }
}
