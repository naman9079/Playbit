package com.playbit.service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component
public class MatchTicker {
    private final MatchService matches;
    public MatchTicker(MatchService matches) { this.matches=matches; }
    @Scheduled(fixedDelay=1000) public void tick() { matches.tickAll(); }
}
