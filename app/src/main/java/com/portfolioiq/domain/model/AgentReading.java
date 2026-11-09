package com.portfolioiq.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What the TradingAgents team read for one stock, as the AI server reports it:
 * the start of the news analyst's report as plain text (at most 700
 * characters, empty if it wrote none), and the data sources the analysts that
 * produced a report can use. The sources come from TradingAgents' configured
 * data vendors; they are not a log of the exact calls the model made.
 */
public class AgentReading {

    private final String newsSummary;
    private final List<String> sources;

    public AgentReading(String newsSummary, List<String> sources) {
        this.newsSummary = newsSummary == null ? "" : newsSummary;
        this.sources = sources == null
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(sources));
    }

    /** Plain text, possibly several lines; empty when there is no news report. */
    public String getNewsSummary() { return newsSummary; }

    /** For example "Yahoo Finance news, past 7 days"; may be empty. */
    public List<String> getSources() { return sources; }
}
