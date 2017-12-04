package dbtb.constraint;

import java.util.SortedSet;

public class DelayedMatchingConstraint<T> implements Constraint<T>{
	
	SortedSet<Integer> matchingPositions;
	
	public DelayedMatchingConstraint(SortedSet<Integer> matchingPositions){
		this.matchingPositions = matchingPositions;
	}

	public SortedSet<Integer> getMatchingPositions() {
		return matchingPositions;
	}
}
