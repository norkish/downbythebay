package automaton;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.Map.Entry;
import java.util.Random;

import org.apache.commons.lang3.time.StopWatch;

import dbtb.constraint.ConditionedConstraint;
import dbtb.constraint.Constraint;
import dbtb.constraint.StateConstraint;
import dbtb.data.DataLoader;
import dbtb.data.SyllableToken;
import dbtb.markov.BidirectionalVariableOrderPrefixIDMap;
import dbtb.markov.SparseVariableOrderMarkovModel;
import dbtb.markov.SparseVariableOrderMarkovModel.CharacterToken;
import dbtb.markov.SparseVariableOrderMarkovModel.CharacterToken.CharacterTokenConstraint;
import dbtb.markov.Token;
import dbtb.markov.UnsatisfiableConstraintSetException;
import dbtb.utils.Pair;
import dbtb.utils.Utils;

public class MatchRandomIteratorBuilderDFS {

	public static <T extends Token> Iterator<List<T>> buildEfficiently(int[] matchConstraintList, boolean[] matchConstraintOutcomeList, SparseVariableOrderMarkovModel<T> markovModel, long timeLimitinMS) {
		List<List<ConditionedConstraint<T>>> controlConstraints = new ArrayList<List<ConditionedConstraint<T>>>();
		
		for (int i = 0; i < matchConstraintList.length; i++) {
			controlConstraints.add(new ArrayList<ConditionedConstraint<T>>());
		}
		
		return buildEfficiently(matchConstraintList, matchConstraintOutcomeList, markovModel, controlConstraints, timeLimitinMS);
	}

	public static <T extends Token> Iterator<List<T>> buildEfficiently(int[] matchConstraintList, boolean[] matchConstraintOutcomeList, SparseVariableOrderMarkovModel<T> markovModel, List<List<ConditionedConstraint<T>>> controlConstraints, long timeLimitinMS) {
		int[][] newMatchConstraintList = new int[1][];
		newMatchConstraintList[0] = matchConstraintList;
		boolean[][] newMatchConstraintOutcomeList = new boolean[1][];
		newMatchConstraintOutcomeList[0] = matchConstraintOutcomeList;
		
		return buildEfficiently(newMatchConstraintList, newMatchConstraintOutcomeList, null, markovModel, controlConstraints, timeLimitinMS);
	}
	
	
	public static <T extends Token> Iterator<List<T>> buildEfficiently(int[] matchConstraintList, boolean[] matchConstraintOutcomeList, 
			List<Comparator<T>> equivalenceRelations, SparseVariableOrderMarkovModel<T> markovModel, long timeLimitinMS) {
		
		int[][] newMatchConstraintList = new int[1][];
		newMatchConstraintList[0] = matchConstraintList;
		
		boolean[][] newMatchConstraintOutcomeList = new boolean[1][];
		newMatchConstraintOutcomeList[0] = matchConstraintOutcomeList;
		
		List<List<ConditionedConstraint<T>>> controlConstraints = new ArrayList<List<ConditionedConstraint<T>>>();
		
		for (int i = 0; i < matchConstraintList.length; i++) {
			controlConstraints.add(new ArrayList<ConditionedConstraint<T>>());
		}
		
		return buildEfficiently(newMatchConstraintList, newMatchConstraintOutcomeList, equivalenceRelations, markovModel, controlConstraints, timeLimitinMS);
	}

	public static <T extends Token> Iterator<List<T>> buildEfficiently(int[][] matchConstraintList, boolean[][] matchConstraintOutcomeList, 
			List<Comparator<T>> equivalenceRelations, SparseVariableOrderMarkovModel<T> markovModel, long timeLimitinMS) {
		List<List<ConditionedConstraint<T>>> controlConstraints = new ArrayList<List<ConditionedConstraint<T>>>();
		
		for (int i = 0; i < matchConstraintList[0].length; i++) {
			controlConstraints.add(new ArrayList<ConditionedConstraint<T>>());
		}
		return buildEfficiently(matchConstraintList, matchConstraintOutcomeList, equivalenceRelations, markovModel, controlConstraints, timeLimitinMS);
	}
	
	public static class MatchIterator<T extends Token> implements Iterator<List<T>>{

		List<T> next;
		boolean computeOnHasNext = true;
		
		int[][] dfsMatchConstraintList;
		boolean[][] dfsMatchConstraintOutcomeList;
		
		Map<Integer, Map<Integer, Double>> validMarkovTransitions;
		
		// 0-based depth and Markov state
		Stack<Pair<Integer,Integer>> visitStack = null;
		Stack<Integer> pathStack = null;

		private List<List<ConditionedConstraint<T>>> controlConstraints;

		private SparseVariableOrderMarkovModel<T> markovModel;
		private List<Comparator<T>> equivalenceRelations;
		private long timeLimit = -1;

		public MatchIterator(int[][] matchConstraintList, boolean[][] matchConstraintOutcomeList, List<Comparator<T>> equivalenceRelations, SparseVariableOrderMarkovModel<T> markovModel, List<List<ConditionedConstraint<T>>> controlConstraints, long timeLimit) {
			if (markovModel.order > matchConstraintList[0].length) throw new RuntimeException("Markov order (" + markovModel.order + ") is greater than desired sequence length (" + matchConstraintList.length + ")");
//			System.out.println("Building iterator");
			this.timeLimit = timeLimit;
			// match constraint list has to be altered so that in traversing depth-first we can look BACK (instead of forward) and match appropriately
			dfsMatchConstraintList = new int[matchConstraintList.length][];
			dfsMatchConstraintOutcomeList = new boolean[matchConstraintOutcomeList.length][];
			for (int constraintSet = 0; constraintSet < matchConstraintList.length; constraintSet++) {
				dfsMatchConstraintList[constraintSet] = new int[matchConstraintList[constraintSet].length];
				Arrays.fill(dfsMatchConstraintList[constraintSet], -1);
				dfsMatchConstraintOutcomeList[constraintSet] = new boolean[matchConstraintOutcomeList[constraintSet].length];
				for (int seqPos = 0; seqPos < matchConstraintList[constraintSet].length; seqPos++) {
					if (matchConstraintList[constraintSet][seqPos] != -1) {
						assert dfsMatchConstraintList[constraintSet][matchConstraintList[constraintSet][seqPos]-1] == -1: "DFS implementation requires that no single match constraint list constrains the same index number twice";
						dfsMatchConstraintList[constraintSet][matchConstraintList[constraintSet][seqPos]-1] = seqPos;
						dfsMatchConstraintOutcomeList[constraintSet][matchConstraintList[constraintSet][seqPos]-1] = matchConstraintOutcomeList[constraintSet][seqPos];
					}
				}
			}
			
			if (equivalenceRelations == null) {
				equivalenceRelations = new ArrayList<Comparator<T>>();
				for (int i = 0; i < dfsMatchConstraintList.length; i++) {
					equivalenceRelations.add(null);
				}
			}
			
			this.equivalenceRelations = equivalenceRelations;
			this.controlConstraints = controlConstraints;
			this.markovModel = markovModel;
			
			validMarkovTransitions = markovModel.logTransitions;

//			System.out.println("DFS model initialized. Searching for valid solution...");
			if (!hasNext()) {
				throw new RuntimeException("Unsatisfiable");
			}
		}
		
		private boolean matchConstraintIsSatisfied(Comparator<T> equivalenceRelation, T t1, T t2) {
			if (equivalenceRelation == null) {// this needs to be adjusted according to the equivalence relation
				return t1.equals(t2);
			} else {//if (equivalenceRelation != null && 
				return equivalenceRelation.compare(t1, t2) == 0;
			}
		}

		@Override
		public boolean hasNext() {
			if (!computeOnHasNext) {
				return (next != null);
			} else {
				StopWatch watch = new StopWatch();
				watch.start();
				computeOnHasNext = false;

				visitStack = new Stack<Pair<Integer,Integer>>();
				pathStack = new Stack<Integer>();
				
				List<Integer> initialStates = probabilisticallySortedKeyset(markovModel.logPriors);
				Collections.reverse(initialStates);

//				Collections.shuffle(initialStates);
				for (Integer state : initialStates) {
					final LinkedList<T> prefixForID = markovModel.stateIndex.getPrefixForID(state);
					boolean keep = true;
					// initial states have to satisfy control constraints at all positions represented in token
					for (int i = 0; keep && i < markovModel.order; i++) { 
						List<ConditionedConstraint<T>> controlConstraintsAti = controlConstraints.get(i); // get control constraints at position
						for (ConditionedConstraint<T> conditionedConstraint : controlConstraintsAti) { // iterate over constraints and check if they're satisfied
							final Constraint<T> constraint = conditionedConstraint.getConstraint();
							if (constraint instanceof StateConstraint && ((StateConstraint<T>)constraint).isSatisfiedBy(prefixForID, i) != conditionedConstraint.getDesiredConditionState()) {
								keep = false;
								break;
							}
						}
						for (int constraintSet = 0; keep && constraintSet < dfsMatchConstraintList.length; constraintSet++) { // for each match constraint set
							final int matchConstraintAtPosForSet = dfsMatchConstraintList[constraintSet][i]; // get matching constraints at position
							if (matchConstraintAtPosForSet != -1) { // if constraint is present
								if (matchConstraintIsSatisfied(equivalenceRelations.get(constraintSet), prefixForID.get(matchConstraintAtPosForSet), prefixForID.get(i)) != dfsMatchConstraintOutcomeList[constraintSet][i]) { // tuple doesn't satisfy match constraint
									keep = false;
									break;
								}
							}
						}
					}
					if (keep) {
						visitStack.push(new Pair<Integer,Integer>(0, state));
					}
				}	
				
				
				Integer currentDepth, currentMarkovState;
				Pair<Integer, Integer> currentDepthAndState;
				
				while(!visitStack.isEmpty()) {
					currentDepthAndState = visitStack.pop();
	//				System.out.println("Popped Depth and State: " + currentDepthAndState);
					currentDepth = currentDepthAndState.getFirst();
					final int nextSeqPos = currentDepth+markovModel.order;
					currentMarkovState = currentDepthAndState.getSecond();
					
					//pop visited path to get to currentDepth
					while(pathStack.size() > currentDepth) {
						pathStack.pop();
					}
					
					pathStack.push(currentMarkovState);
					final Map<Integer, Double> validMarkovTransitionsFromLabel = validMarkovTransitions.get(currentMarkovState);
					
					List<ConditionedConstraint<T>> controlConstraintsAti = controlConstraints.get(nextSeqPos);
					List<Integer> nextStates = validMarkovTransitionsFromLabel == null ? new ArrayList<Integer>() : probabilisticallySortedKeyset(validMarkovTransitionsFromLabel);
					Collections.reverse(nextStates);

//					Collections.shuffle(nextStates);
					for (Integer validMarkovTransition : nextStates) {
						final LinkedList<T> prefixForID = markovModel.stateIndex.getPrefixForID(validMarkovTransition);
						boolean keep = true;
						//control constraints
						for (ConditionedConstraint<T> conditionedConstraint : controlConstraintsAti) {
	//						System.out.println("Checking control constraint");
							final Constraint<T> constraint = conditionedConstraint.getConstraint();
							if (constraint instanceof StateConstraint && ((StateConstraint<T>)constraint).isSatisfiedBy(prefixForID, prefixForID.size()-1) != conditionedConstraint.getDesiredConditionState()) {
								keep = false;
								break;
							}
						}
						
						//match constraints
						for (int constraintSet = 0; keep && constraintSet < dfsMatchConstraintList.length; constraintSet++) {
							final int matchConstraintAtPosForSet = dfsMatchConstraintList[constraintSet][nextSeqPos];
							if (matchConstraintAtPosForSet != -1) {
								//	System.out.println("Checking match constraint");
								T prevTokenToMatch = (matchConstraintAtPosForSet < markovModel.order ? 
										markovModel.stateIndex.getPrefixForID(pathStack.get(0)).get(matchConstraintAtPosForSet) :
										markovModel.stateIndex.getPrefixForID(pathStack.get(matchConstraintAtPosForSet-markovModel.order+1)).getLast());
								if (matchConstraintIsSatisfied(equivalenceRelations.get(constraintSet), prevTokenToMatch, prefixForID.getLast()) != dfsMatchConstraintOutcomeList[constraintSet][nextSeqPos]) { // tuple doesn't satisfy match constraint
									keep = false;
									break;
								}
							}
						}
						if (keep) {
							if (nextSeqPos+1 == dfsMatchConstraintList[0].length) {
								next = new ArrayList<T>();
								next.addAll(markovModel.stateIndex.getPrefixForID(pathStack.get(0)));
								boolean first = true;
								for (Integer integer : pathStack) {
									if (first)
										first = false;
									else
										next.add(markovModel.stateIndex.getPrefixFinaleForID(integer));
								}
								next.add(markovModel.stateIndex.getPrefixFinaleForID(validMarkovTransition));
								return true;
							} else {
	//							System.out.println("Pushing " + nextDepthAndState);
								visitStack.push(new Pair<Integer,Integer>(currentDepth+1, validMarkovTransition));
							}
						}
					}
					
//					if (computePercentTotalMemoryUsed() > 60) {
//						System.out.println("Memory limit reached. Used " + (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory())/1000000 + " MB.");
//						break;
//					} else 
					if (timeLimit  != -1 && watch.getTime() > timeLimit) {
						System.out.println("Time limit reached");
						break;
					}
				}
				
				next = null;
				return false;
			}
		}

		Random rand = new Random();
		/**
		 * Create a semi-sorted list where tokens are sorted according to the order in which they are probabilistically sampled from the input distribution.
		 * Tokens are only sampled once.
		 * @param validMarkovTransitions
		 * @return
		 */
		private List<Integer> probabilisticallySortedKeyset(Map<Integer, Double> validMarkovTransitions) {
			List<Integer> probabilisticallySortedKeyset = new ArrayList<Integer>();
			
			Map<Integer, Double> validMarkovTransitionsCopy = new HashMap<Integer, Double>(validMarkovTransitions);
			//keep track of how much probability density is left in the copy, we assume it initally sums to 1.0
			double sumProb = 1.0;

			//sample remaining distribution
			while(!validMarkovTransitionsCopy.isEmpty()) {
				Integer toRemove = -1;
				double r = rand.nextDouble() * sumProb;
				double sampleSum = 0.0;
				for (Entry<Integer, Double> entry : validMarkovTransitionsCopy.entrySet()) {
					final Double prob = Math.exp(entry.getValue());
					sampleSum += prob;
					if (sampleSum >= r) { 
						toRemove = entry.getKey();
						sumProb -= prob;
						break;
					}
				}
				
				//add sampled label (removed from remainin distribution) to sorted set
				validMarkovTransitionsCopy.remove(toRemove);
				probabilisticallySortedKeyset.add(toRemove);
			}
						
			return probabilisticallySortedKeyset;
		}

		@Override
		public List<T> next() {
			computeOnHasNext = true;
			return next;
		}
		
	}
	
	public static double computePercentTotalMemoryUsed() {
		return (100.0*(Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()))/Runtime.getRuntime().maxMemory();
	}
	
	public static <T extends Token> Iterator<List<T>> buildEfficiently(int[][] matchConstraintList, boolean[][] matchConstraintOutcomeList, List<Comparator<T>> equivalenceRelations, SparseVariableOrderMarkovModel<T> markovModel, List<List<ConditionedConstraint<T>>> controlConstraints, long timeLimitinMS) {
		return new MatchIterator<T>(matchConstraintList, matchConstraintOutcomeList, equivalenceRelations, markovModel, controlConstraints, timeLimitinMS);
	}

	private static void runExample1() throws UnsatisfiableConstraintSetException, InterruptedException {
		int markovOrder = 1;
		
		// Markov Model
		Map<Integer, Double> priors = new HashMap<Integer, Double>();
		Map<Integer, Map<Integer, Double>> transitions = new HashMap<Integer, Map<Integer, Double>>();
		BidirectionalVariableOrderPrefixIDMap<SyllableToken> prefixMap = new BidirectionalVariableOrderPrefixIDMap<SyllableToken>(markovOrder);

		String[] trainingSentences = new String[]{"dead bear with Ed with bed hair"};
		DataLoader dl = new DataLoader(markovOrder);
		
		for (String trainingSentence : trainingSentences) {
			List<SyllableToken> trainingSentenceTokens = DataLoader.convertToSyllableTokens(dl.cleanSentence(trainingSentence)).get(0);
			LinkedList<SyllableToken> prefix = new LinkedList<SyllableToken>(trainingSentenceTokens.subList(0, markovOrder));
			Integer toTokenID;
			Integer fromTokenID = prefixMap.addPrefix(prefix);
			for (int j = markovOrder; j < trainingSentenceTokens.size(); j++ ) {
				prefix.removeFirst();
				prefix.addLast(trainingSentenceTokens.get(j));
				
				toTokenID = prefixMap.addPrefix(prefix);
				Utils.incrementValueForKeys(transitions, fromTokenID, toTokenID, 1.0);
				Utils.incrementValueForKey(priors, fromTokenID, 1.0); // we do this for every token 
				
				fromTokenID = toTokenID;
			}
		}
		Utils.normalize(priors);
		Utils.normalizeByFirstDimension(transitions);
		
		SparseVariableOrderMarkovModel<SyllableToken> M = new SparseVariableOrderMarkovModel<SyllableToken>(prefixMap,priors,transitions);
		
		int[] matchConstraintList = new int[]{4,5,-1,-1,-1};
		int length = matchConstraintList.length;
		
		List<Comparator<SyllableToken>> equivalenceRelations = new ArrayList<Comparator<SyllableToken>>(){{
			add(new RhymeComparator());
		}};
		
		boolean[] matchConstraintOutcomeList = new boolean[matchConstraintList.length];
		Arrays.fill(matchConstraintOutcomeList, true);
		Iterator<List<SyllableToken>> A = buildEfficiently(matchConstraintList, matchConstraintOutcomeList, equivalenceRelations, M, -1);
		
		while(A.hasNext()) {
			System.out.println(A.next());
		}
	}

	private static void runExample2() throws UnsatisfiableConstraintSetException, InterruptedException {
		int markovOrder = 1;
		
		// Markov Model
		Map<Integer, Double> priors = new HashMap<Integer, Double>();
		Map<Integer, Map<Integer, Double>> transitions = new HashMap<Integer, Map<Integer, Double>>();
		BidirectionalVariableOrderPrefixIDMap<SyllableToken> prefixMap = new BidirectionalVariableOrderPrefixIDMap<SyllableToken>(markovOrder);

		String[] trainingSentences = new String[]{"Ed is dead", "Ed was dead"};
		DataLoader dl = new DataLoader(markovOrder);
		
		for (String trainingSentence : trainingSentences) {
			List<SyllableToken> trainingSentenceTokens = DataLoader.convertToSyllableTokens(dl.cleanSentence(trainingSentence)).get(0);
			LinkedList<SyllableToken> prefix = new LinkedList<SyllableToken>(trainingSentenceTokens.subList(0, markovOrder));
			Integer toTokenID;
			Integer fromTokenID = prefixMap.addPrefix(prefix);
			for (int j = markovOrder; j < trainingSentenceTokens.size(); j++ ) {
				prefix.removeFirst();
				prefix.addLast(trainingSentenceTokens.get(j));
				
				toTokenID = prefixMap.addPrefix(prefix);
				Utils.incrementValueForKeys(transitions, fromTokenID, toTokenID, 1.0);
				Utils.incrementValueForKey(priors, fromTokenID, 1.0); // we do this for every token 
				
				fromTokenID = toTokenID;
			}
		}
		Utils.normalize(priors);
		Utils.normalizeByFirstDimension(transitions);
		
		SparseVariableOrderMarkovModel<SyllableToken> M = new SparseVariableOrderMarkovModel<SyllableToken>(prefixMap,priors,transitions);
		
		int[] matchConstraintList = new int[]{3,-1,-1}; // 1-based
		int length = matchConstraintList.length;
		
		List<Comparator<SyllableToken>> equivalenceRelations = new ArrayList<Comparator<SyllableToken>>(){{
			add(new RhymeComparator());
		}};
		
		boolean[] matchConstraintOutcomeList = new boolean[matchConstraintList.length];
		Arrays.fill(matchConstraintOutcomeList, true);
		
		Iterator<List<SyllableToken>> A = buildEfficiently(matchConstraintList, matchConstraintOutcomeList, equivalenceRelations, M, -1);
		
		while(A.hasNext()) {
			System.out.println(A.next());
		}
	}
	
	private static void runExample3() throws UnsatisfiableConstraintSetException, InterruptedException {
		int markovOrder = 1;
		
		// Markov Model
		Map<Integer, Double> priors = new HashMap<Integer, Double>();
		Map<Integer, Map<Integer, Double>> transitions = new HashMap<Integer, Map<Integer, Double>>();
		BidirectionalVariableOrderPrefixIDMap<SyllableToken> prefixMap = new BidirectionalVariableOrderPrefixIDMap<SyllableToken>(markovOrder);

		String[] trainingSentences = new String[]{"Have you seen a moose with a pair of new shoes?","Have you ever seen a bear combing his hair?"};
		DataLoader dl = new DataLoader(markovOrder);
		
		for (String trainingSentence : trainingSentences) {
			List<SyllableToken> trainingSentenceTokens = DataLoader.convertToSyllableTokens(dl.cleanSentence(trainingSentence)).get(0);
			LinkedList<SyllableToken> prefix = new LinkedList<SyllableToken>(trainingSentenceTokens.subList(0, markovOrder));
			Integer toTokenID;
			Integer fromTokenID = prefixMap.addPrefix(prefix);
			for (int j = markovOrder; j < trainingSentenceTokens.size(); j++ ) {
				prefix.removeFirst();
				prefix.addLast(trainingSentenceTokens.get(j));
				
				toTokenID = prefixMap.addPrefix(prefix);
				Utils.incrementValueForKeys(transitions, fromTokenID, toTokenID, 1.0);
				Utils.incrementValueForKey(priors, fromTokenID, 1.0); // we do this for every token 
				
				fromTokenID = toTokenID;
			}
		}
		Utils.normalize(priors);
		Utils.normalizeByFirstDimension(transitions);
		
		SparseVariableOrderMarkovModel<SyllableToken> M = new SparseVariableOrderMarkovModel<SyllableToken>(prefixMap,priors,transitions);
		
		int[] matchConstraintList = new int[]{-1,-1,-1,-1,-1,-1,13,-1,-1,-1,-1,-1,-1}; // 1-based
		int length = matchConstraintList.length;
		
		List<Comparator<SyllableToken>> equivalenceRelations = new ArrayList<Comparator<SyllableToken>>(){{
			add(new RhymeComparator());
		}};
		
		boolean[] matchConstraintOutcomeList = new boolean[matchConstraintList.length];
		Arrays.fill(matchConstraintOutcomeList, true);
		
		try {
			Iterator<List<SyllableToken>> A = buildEfficiently(matchConstraintList, matchConstraintOutcomeList, equivalenceRelations, M, -1);
			
			while(A.hasNext()) {
				System.out.println(A.next());
			}
		} catch (Exception e) {
			System.out.println("Not satisfiable");
		}
	}
	
	private static void runExample4() throws UnsatisfiableConstraintSetException, InterruptedException {
		int markovOrder = 1;
		
		// Markov Model
		BidirectionalVariableOrderPrefixIDMap<CharacterToken> prefixMap = new BidirectionalVariableOrderPrefixIDMap<CharacterToken>(markovOrder);
		Map<Integer, Map<Integer, Double>> transitions = new HashMap<Integer, Map<Integer, Double>>();
		Map<Integer, Double> priors = new HashMap<Integer, Double>();
		
		CharacterToken a = new CharacterToken('a');
		LinkedList<CharacterToken> aToken = new LinkedList<CharacterToken>();
		aToken.add(a);
		Integer aTokenIdx = prefixMap.addPrefix(aToken);

		CharacterToken b = new CharacterToken('b');
		LinkedList<CharacterToken> bToken = new LinkedList<CharacterToken>();
		bToken.add(b);
		Integer bTokenIdx = prefixMap.addPrefix(bToken);
		
		priors.put(aTokenIdx, .25);
		priors.put(bTokenIdx, .75);
		
		Map<Integer,Double> aTransitions = new HashMap<Integer,Double>();
		transitions.put(aTokenIdx, aTransitions);
		aTransitions.put(aTokenIdx, .01);
		aTransitions.put(bTokenIdx, .99);
		
		Map<Integer,Double> bTransitions = new HashMap<Integer,Double>();
		transitions.put(bTokenIdx, bTransitions);
		bTransitions.put(aTokenIdx, 0.8);
		bTransitions.put(bTokenIdx, 0.2);
		
		SparseVariableOrderMarkovModel<CharacterToken> M = new SparseVariableOrderMarkovModel<CharacterToken>(prefixMap,priors,transitions);
		
		int[] matchConstraintList = new int[]{3,-1,-1,6,-1,-1}; // 1-based
		
		boolean[] matchConstraintOutcomeList = new boolean[matchConstraintList.length];
		Arrays.fill(matchConstraintOutcomeList, true);
		
		Iterator<List<CharacterToken>> A = buildEfficiently(matchConstraintList, matchConstraintOutcomeList, M, -1);
		
		while(A.hasNext()) {
			System.out.println(A.next());
		}
	}
	
	private static void runExample5() throws UnsatisfiableConstraintSetException, InterruptedException {
		int markovOrder = 1;
		
		// Markov Model
		BidirectionalVariableOrderPrefixIDMap<CharacterToken> prefixMap = new BidirectionalVariableOrderPrefixIDMap<CharacterToken>(markovOrder);
		Map<Integer, Map<Integer, Double>> transitions = new HashMap<Integer, Map<Integer, Double>>();
		Map<Integer, Double> priors = new HashMap<Integer, Double>();
		
		CharacterToken a = new CharacterToken('a');
		LinkedList<CharacterToken> aToken = new LinkedList<CharacterToken>();
		aToken.add(a);
		Integer aTokenIdx = prefixMap.addPrefix(aToken);

		CharacterToken b = new CharacterToken('b');
		LinkedList<CharacterToken> bToken = new LinkedList<CharacterToken>();
		bToken.add(b);
		Integer bTokenIdx = prefixMap.addPrefix(bToken);
		
		priors.put(aTokenIdx, .25);
		priors.put(bTokenIdx, .75);
		
		Map<Integer,Double> aTransitions = new HashMap<Integer,Double>();
		transitions.put(aTokenIdx, aTransitions);
		aTransitions.put(aTokenIdx, .01);
		aTransitions.put(bTokenIdx, .99);
		
		Map<Integer,Double> bTransitions = new HashMap<Integer,Double>();
		transitions.put(bTokenIdx, bTransitions);
		bTransitions.put(aTokenIdx, 0.8);
		bTransitions.put(bTokenIdx, 0.2);
		
		SparseVariableOrderMarkovModel<CharacterToken> M = new SparseVariableOrderMarkovModel<CharacterToken>(prefixMap,priors,transitions);
		
		int[] matchConstraintList = new int[]{3,-1,5,-1,-1}; // 1-based
		
		boolean[] matchConstraintOutcomeList = new boolean[matchConstraintList.length];
		Arrays.fill(matchConstraintOutcomeList, true);
		
		Iterator<List<CharacterToken>> A = buildEfficiently(matchConstraintList, matchConstraintOutcomeList, M, -1);
		
		while(A.hasNext()) {
			System.out.println(A.next());
		}
	}
	
	private static void runExample6() throws UnsatisfiableConstraintSetException, InterruptedException {
		int markovOrder = 1;
		
		// Markov Model
		BidirectionalVariableOrderPrefixIDMap<CharacterToken> prefixMap = new BidirectionalVariableOrderPrefixIDMap<CharacterToken>(markovOrder);
		Map<Integer, Map<Integer, Double>> transitions = new HashMap<Integer, Map<Integer, Double>>();
		Map<Integer, Double> priors = new HashMap<Integer, Double>();
		
		CharacterToken a = new CharacterToken('a');
		LinkedList<CharacterToken> aToken = new LinkedList<CharacterToken>();
		aToken.add(a);
		Integer aTokenIdx = prefixMap.addPrefix(aToken);

		CharacterToken b = new CharacterToken('b');
		LinkedList<CharacterToken> bToken = new LinkedList<CharacterToken>();
		bToken.add(b);
		Integer bTokenIdx = prefixMap.addPrefix(bToken);
		
		priors.put(aTokenIdx, .25);
		priors.put(bTokenIdx, .75);
		
		Map<Integer,Double> aTransitions = new HashMap<Integer,Double>();
		transitions.put(aTokenIdx, aTransitions);
		aTransitions.put(aTokenIdx, .01);
		aTransitions.put(bTokenIdx, .99);
		
		Map<Integer,Double> bTransitions = new HashMap<Integer,Double>();
		transitions.put(bTokenIdx, bTransitions);
		bTransitions.put(aTokenIdx, 0.8);
		bTransitions.put(bTokenIdx, 0.2);
		
		SparseVariableOrderMarkovModel<CharacterToken> M = new SparseVariableOrderMarkovModel<CharacterToken>(prefixMap,priors,transitions);
		
		int[] matchConstraintList = new int[]{3,-1,5,-1,-1}; // 1-based
		
		List<List<ConditionedConstraint<CharacterToken>>> controlConstraints = new ArrayList<List<ConditionedConstraint<CharacterToken>>>();	
		for (int i = 0; i < matchConstraintList.length; i++) {
			controlConstraints.add(new ArrayList<ConditionedConstraint<CharacterToken>>());
		}
		controlConstraints.get(1).add(new ConditionedConstraint<>(new CharacterTokenConstraint<CharacterToken>(new CharacterToken('a'))));
		
		boolean[] matchConstraintOutcomeList = new boolean[matchConstraintList.length];
		Arrays.fill(matchConstraintOutcomeList, true);
		
		Iterator<List<CharacterToken>> A = buildEfficiently(matchConstraintList, matchConstraintOutcomeList, M, controlConstraints, -1);
		
		while(A.hasNext()) {
			System.out.println(A.next());
		}
	}
	
	private static void runExample7() throws UnsatisfiableConstraintSetException, InterruptedException {
		int markovOrder = 1;
		
		// Markov Model
		Map<Integer, Double> priors = new HashMap<Integer, Double>();
		Map<Integer, Map<Integer, Double>> transitions = new HashMap<Integer, Map<Integer, Double>>();
		BidirectionalVariableOrderPrefixIDMap<SyllableToken> prefixMap = new BidirectionalVariableOrderPrefixIDMap<SyllableToken>(markovOrder);

		String[] trainingSentences = new String[]{"one star how are world high in sky one star how are"};
		DataLoader dl = new DataLoader(markovOrder);
		
		for (String trainingSentence : trainingSentences) {
			List<SyllableToken> trainingSentenceTokens = DataLoader.convertToSyllableTokens(dl.cleanSentence(trainingSentence)).get(0);
			LinkedList<SyllableToken> prefix = new LinkedList<SyllableToken>(trainingSentenceTokens.subList(0, markovOrder));
			Integer toTokenID;
			Integer fromTokenID = prefixMap.addPrefix(prefix);
			for (int j = markovOrder; j < trainingSentenceTokens.size(); j++ ) {
				prefix.removeFirst();
				prefix.addLast(trainingSentenceTokens.get(j));
				
				toTokenID = prefixMap.addPrefix(prefix);
				Utils.incrementValueForKeys(transitions, fromTokenID, toTokenID, 1.0);
				Utils.incrementValueForKey(priors, fromTokenID, 1.0); // we do this for every token 
				
				fromTokenID = toTokenID;
			}
			Utils.incrementValueForKey(priors, fromTokenID, 1.0); // we do this for every token 
		}
		Utils.normalize(priors);
		Utils.normalizeByFirstDimension(transitions);
		
		SparseVariableOrderMarkovModel<SyllableToken> M = new SparseVariableOrderMarkovModel<SyllableToken>(prefixMap,priors,transitions);
		
		int[][] matchConstraintList = new int[][]{new int[]{9,10,11,12,-1,-1,-1,-1,-1,-1,-1,-1}, new int[]{-1,4,-1,-1,-1,8,-1,-1,-1,12,-1,-1}}; // 1-based
		
		List<Comparator<SyllableToken>> equivalenceRelations = new ArrayList<Comparator<SyllableToken>>(){{
			add(null);
			add(new RhymeComparator());
		}};
		
		boolean[][] matchConstraintOutcomeList = new boolean[2][matchConstraintList[0].length];
		for (boolean[] bs : matchConstraintOutcomeList) {
			Arrays.fill(bs, true);
		}
		
		Iterator<List<SyllableToken>> A = buildEfficiently(matchConstraintList, matchConstraintOutcomeList, equivalenceRelations, M, -1);
		
		while(A.hasNext()) {
			System.out.println(A.next());
		}
	}
	
	private static void runExample8() throws UnsatisfiableConstraintSetException, InterruptedException {
		int markovOrder = 5;
		
		// Markov Model
		Map<Integer, Double> priors = new HashMap<Integer, Double>();
		Map<Integer, Map<Integer, Double>> transitions = new HashMap<Integer, Map<Integer, Double>>();
		BidirectionalVariableOrderPrefixIDMap<SyllableToken> prefixMap = new BidirectionalVariableOrderPrefixIDMap<SyllableToken>(markovOrder);

		String[] trainingSentences = new String[]{"one star how are world high in sky one star how are"};
		DataLoader dl = new DataLoader(markovOrder);
		
		for (String trainingSentence : trainingSentences) {
			List<SyllableToken> trainingSentenceTokens = DataLoader.convertToSyllableTokens(dl.cleanSentence(trainingSentence)).get(0);
			LinkedList<SyllableToken> prefix = new LinkedList<SyllableToken>(trainingSentenceTokens.subList(0, markovOrder));
			Integer toTokenID;
			Integer fromTokenID = prefixMap.addPrefix(prefix);
			for (int j = markovOrder; j < trainingSentenceTokens.size(); j++ ) {
				prefix.removeFirst();
				prefix.addLast(trainingSentenceTokens.get(j));
				
				toTokenID = prefixMap.addPrefix(prefix);
				Utils.incrementValueForKeys(transitions, fromTokenID, toTokenID, 1.0);
				Utils.incrementValueForKey(priors, fromTokenID, 1.0); // we do this for every token 
				
				fromTokenID = toTokenID;
			}
			Utils.incrementValueForKey(priors, fromTokenID, 1.0); // we do this for every token 
		}
		Utils.normalize(priors);
		Utils.normalizeByFirstDimension(transitions);
		
		SparseVariableOrderMarkovModel<SyllableToken> M = new SparseVariableOrderMarkovModel<SyllableToken>(prefixMap,priors,transitions);
		
		int[][] matchConstraintList = new int[][]{new int[]{9,10,11,12,-1,-1,-1,-1,-1,-1,-1,-1}, new int[]{-1,4,-1,-1,-1,8,-1,-1,-1,12,-1,-1}}; // 1-based
		
		List<Comparator<SyllableToken>> equivalenceRelations = new ArrayList<Comparator<SyllableToken>>(){{
			add(null);
			add(new RhymeComparator());
		}};
		
		boolean[][] matchConstraintOutcomeList = new boolean[2][matchConstraintList[0].length];
		for (boolean[] bs : matchConstraintOutcomeList) {
			Arrays.fill(bs, true);
		}
		
		Iterator<List<SyllableToken>> A = buildEfficiently(matchConstraintList, matchConstraintOutcomeList, equivalenceRelations, M, -1);
		
		while(A.hasNext()) {
			System.out.println(A.next());
		}
	}

	public static void main(String[] args) throws UnsatisfiableConstraintSetException, InterruptedException {
//		runExample1();
//		runExample2();
//		runExample3();
//		runExample4();
//		runExample5();
//		runExample6();
//		runExample7();
		runExample8();
	}
}
