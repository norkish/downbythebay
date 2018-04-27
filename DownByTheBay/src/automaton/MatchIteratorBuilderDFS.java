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

public class MatchIteratorBuilderDFS {

	public static <T extends Token> Iterator<List<T>> buildEfficiently(int[] matchConstraintList, boolean[] matchConstraintOutcomeList, SparseVariableOrderMarkovModel<T> markovModel) {
		List<List<ConditionedConstraint<T>>> controlConstraints = new ArrayList<List<ConditionedConstraint<T>>>();
		
		for (int i = 0; i < matchConstraintList.length; i++) {
			controlConstraints.add(new ArrayList<ConditionedConstraint<T>>());
		}
		
		return buildEfficiently(matchConstraintList, matchConstraintOutcomeList, markovModel, controlConstraints);
	}

	public static <T extends Token> Iterator<List<T>> buildEfficiently(int[] matchConstraintList, boolean[] matchConstraintOutcomeList, SparseVariableOrderMarkovModel<T> markovModel, List<List<ConditionedConstraint<T>>> controlConstraints) {
		int[][] newMatchConstraintList = new int[1][];
		newMatchConstraintList[0] = matchConstraintList;
		boolean[][] newMatchConstraintOutcomeList = new boolean[1][];
		newMatchConstraintOutcomeList[0] = matchConstraintOutcomeList;
		return buildEfficiently(newMatchConstraintList, newMatchConstraintOutcomeList, null, markovModel, controlConstraints);
	}
	
	
	public static <T extends Token> Iterator<List<T>> buildEfficiently(int[] matchConstraintList, boolean[] matchConstraintOutcomeList, 
			List<Comparator<T>> equivalenceRelations, SparseVariableOrderMarkovModel<T> markovModel) {
		
		int[][] newMatchConstraintList = new int[1][];
		newMatchConstraintList[0] = matchConstraintList;
		
		boolean[][] newMatchConstraintOutcomeList = new boolean[1][];
		newMatchConstraintOutcomeList[0] = matchConstraintOutcomeList;
		
		List<List<ConditionedConstraint<T>>> controlConstraints = new ArrayList<List<ConditionedConstraint<T>>>();
		
		for (int i = 0; i < matchConstraintList.length; i++) {
			controlConstraints.add(new ArrayList<ConditionedConstraint<T>>());
		}
		
		return buildEfficiently(newMatchConstraintList, newMatchConstraintOutcomeList, equivalenceRelations, markovModel, controlConstraints);
	}

	public static <T extends Token> Iterator<List<T>> buildEfficiently(int[][] matchConstraintList, boolean[][] matchConstraintOutcomeList, 
			List<Comparator<T>> equivalenceRelations, SparseVariableOrderMarkovModel<T> markovModel) {
		List<List<ConditionedConstraint<T>>> controlConstraints = new ArrayList<List<ConditionedConstraint<T>>>();
		
		for (int i = 0; i < matchConstraintList[0].length; i++) {
			controlConstraints.add(new ArrayList<ConditionedConstraint<T>>());
		}
		return buildEfficiently(matchConstraintList, matchConstraintOutcomeList, equivalenceRelations, markovModel, controlConstraints);
	}
	
	public static class MatchIterator<T extends Token> implements Iterator<List<T>>{

		List<T> next;
		boolean computeOnHasNext = true;
		
		int[][] dfsMatchConstraintList;
		boolean[][] dfsMatchConstraintOutcomeList;
		
		int[][] equivalenceClassMap;
		Map<Integer, Map<Integer, Double>> validMarkovTransitions;
		
		// 0-based depth and Markov state
		Stack<Pair<Integer,Integer>> visitStack = new Stack<Pair<Integer,Integer>>();
		Stack<Integer> pathStack = new Stack<Integer>();

		private List<List<ConditionedConstraint<T>>> controlConstraints;

		private SparseVariableOrderMarkovModel<T> markovModel;

		public MatchIterator(int[][] matchConstraintList, boolean[][] matchConstraintOutcomeList, List<Comparator<T>> equivalenceRelations, SparseVariableOrderMarkovModel<T> markovModel, List<List<ConditionedConstraint<T>>> controlConstraints) {
//			System.out.println("Building iterator");
			dfsMatchConstraintList = new int[matchConstraintList.length][];
			dfsMatchConstraintOutcomeList = new boolean[matchConstraintOutcomeList.length][];
			for (int i = 0; i < matchConstraintList.length; i++) {
				dfsMatchConstraintList[i] = new int[matchConstraintList[i].length];
				Arrays.fill(dfsMatchConstraintList[i], -1);
				dfsMatchConstraintOutcomeList[i] = new boolean[matchConstraintOutcomeList[i].length];
				for (int j = 0; j < matchConstraintList[i].length; j++) {
					if (matchConstraintList[i][j] != -1) {
						dfsMatchConstraintList[i][matchConstraintList[i][j]-1] = j;
						dfsMatchConstraintOutcomeList[i][matchConstraintList[i][j]-1] = matchConstraintOutcomeList[i][j];
					}
				}
			}
			
			equivalenceClassMap = computeEquivalenceClasses(markovModel.stateIndex, equivalenceRelations, matchConstraintList.length);

			this.controlConstraints = controlConstraints;
			this.markovModel = markovModel;
			
			List<ConditionedConstraint<T>> controlConstraintsAti = controlConstraints.get(0);
			final List<Integer> initialStates = new ArrayList<Integer>(markovModel.logPriors.keySet());
			Collections.shuffle(initialStates);
			for (Integer state : initialStates) {
				boolean keep = true;
				for (ConditionedConstraint<T> conditionedConstraint : controlConstraintsAti) {
					final Constraint<T> constraint = conditionedConstraint.getConstraint();
					final LinkedList<T> prefixForID = markovModel.stateIndex.getPrefixForID(state);
					if (constraint instanceof StateConstraint && ((StateConstraint<T>)constraint).isSatisfiedBy(prefixForID, 0) != conditionedConstraint.getDesiredConditionState()) {
						keep = false;
						break;
					}
				}
				if (keep) {
					visitStack.push(new Pair<Integer,Integer>(0, state));
				}
			}		
			
			validMarkovTransitions = markovModel.logTransitions;

			System.out.println("DFS model initialized. Searching for valid solution...");
			if (!hasNext()) {
				throw new RuntimeException("Unsatisfiable");
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
				Integer currentDepth, currentMarkovState;
				Pair<Integer, Integer> currentDepthAndState;
				int equivalenceClassAtMatchPos;
				
				while(!visitStack.isEmpty()) {
					currentDepthAndState = visitStack.pop();
	//				System.out.println("Popped Depth and State: " + currentDepthAndState);
					currentDepth = currentDepthAndState.getFirst();
					final int nextDepth = currentDepth+1;
					currentMarkovState = currentDepthAndState.getSecond();
					
					//pop visited path to get to currentDepth
					while(pathStack.size() > currentDepth) {
						pathStack.pop();
					}
					
					pathStack.push(currentMarkovState);
					final Map<Integer, Double> validMarkovTransitionsFromLabel = validMarkovTransitions.get(currentMarkovState);
					
					List<ConditionedConstraint<T>> controlConstraintsAti = controlConstraints.get(nextDepth);
					final List<Integer> nextStates = validMarkovTransitionsFromLabel == null ? new ArrayList<Integer>() : new ArrayList<Integer>(validMarkovTransitionsFromLabel.keySet());
					Collections.shuffle(nextStates);
					for (Integer validMarkovTransition : nextStates) {
						boolean keep = true;
						//control constraints
						for (ConditionedConstraint<T> conditionedConstraint : controlConstraintsAti) {
	//						System.out.println("Checking control constraint");
							final Constraint<T> constraint = conditionedConstraint.getConstraint();
							final LinkedList<T> prefixForID = markovModel.stateIndex.getPrefixForID(validMarkovTransition);
							if (constraint instanceof StateConstraint && ((StateConstraint<T>)constraint).isSatisfiedBy(prefixForID, 0) != conditionedConstraint.getDesiredConditionState()) {
								keep = false;
								break;
							}
						}
						
						//match constraints
						for (int j = 0; j < dfsMatchConstraintList.length; j++) {
							if (dfsMatchConstraintList[j][nextDepth] != -1) {
	//							System.out.println("Checking match constraint");
								equivalenceClassAtMatchPos = equivalenceClassMap[j][pathStack.elementAt(dfsMatchConstraintList[j][nextDepth])];
								if ((equivalenceClassAtMatchPos == equivalenceClassMap[j][validMarkovTransition]) != dfsMatchConstraintOutcomeList[j][nextDepth]) {
	//								System.out.println("" + dfsMatchConstraintList[j][nextDepth] + "≠" + nextDepth);
									keep = false;
									break;
								}
							}
						}
						if (keep) {
							if (nextDepth+1 == dfsMatchConstraintList[0].length) {
								next = new ArrayList<T>();
								for (Integer integer : pathStack) {
									next.add(markovModel.stateIndex.getPrefixFinaleForID(integer));
								}
								next.add(markovModel.stateIndex.getPrefixFinaleForID(validMarkovTransition));
								return true;
							} else {
	//							System.out.println("Pushing " + nextDepthAndState);
								visitStack.push(new Pair<Integer,Integer>(nextDepth, validMarkovTransition));
							}
						}
					}
					
					if (computePercentTotalMemoryUsed() > 40) {
						System.out.println("Memory limit reached");
						break;
					} else if (watch.getTime() > 20000) {
						System.out.println("Time limit reached");
						break;
					}
				}
				
				next = null;
				return false;
			}
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
	
	public static <T extends Token> Iterator<List<T>> buildEfficiently(int[][] matchConstraintList, boolean[][] matchConstraintOutcomeList, List<Comparator<T>> equivalenceRelations, SparseVariableOrderMarkovModel<T> markovModel, List<List<ConditionedConstraint<T>>> controlConstraints) {
		
		return new MatchIterator<T>(matchConstraintList, matchConstraintOutcomeList, equivalenceRelations, markovModel, controlConstraints);
	}

	private static <T extends Token> int[][] computeEquivalenceClasses(BidirectionalVariableOrderPrefixIDMap<T> stateIndex, List<Comparator<T>> equivalenceRelations, int numRelations) {
		
		List<LinkedList<T>> idToPrefixMap = stateIndex.getIDToPrefixMap();		
		int[][] equivalenceClassMaps = new int[equivalenceRelations == null? numRelations : equivalenceRelations.size()][idToPrefixMap.size()];
		
		int[] equivalenceClassMap;
		for (int relationId = 0; relationId < equivalenceClassMaps.length; relationId++) {
			Comparator<T> equivalenceRelation = equivalenceRelations == null? null : equivalenceRelations.get(relationId);
			equivalenceClassMap = equivalenceClassMaps[relationId];
			List<T> equivalenceClassRepresentatives = new ArrayList<T>();
			for (int tokenId = 0; tokenId < idToPrefixMap.size(); tokenId++) {
				T token = idToPrefixMap.get(tokenId).getLast();
				boolean classFound = false;
				for (int equivalenceClassId = 0; equivalenceClassId < equivalenceClassRepresentatives.size(); equivalenceClassId++) {
					T representative = equivalenceClassRepresentatives.get(equivalenceClassId);
					if (equivalenceRelation == null && token.equals(representative)) { // this needs to be adjusted according to the equivalence relation
						equivalenceClassMap[tokenId] = equivalenceClassId;
						classFound = true;
						break;
					} else if (equivalenceRelation != null && equivalenceRelation.compare(token, representative) == 0) {
						equivalenceClassMap[tokenId] = equivalenceClassId;
						classFound = true;
						break;
					}
				}
				
				if (!classFound) {
					equivalenceClassMap[tokenId] = equivalenceClassRepresentatives.size();
					equivalenceClassRepresentatives.add(token);
				}
			}
		}
		
		
		return equivalenceClassMaps;
	}

	public static void main(String[] args) throws UnsatisfiableConstraintSetException, InterruptedException {
		runExample1();
		runExample2();
		runExample3();
		runExample4();
		runExample5();
		runExample6();
		runExample7();
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
		Iterator<List<SyllableToken>> A = buildEfficiently(matchConstraintList, matchConstraintOutcomeList, equivalenceRelations, M);
		
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
		
		Iterator<List<SyllableToken>> A = buildEfficiently(matchConstraintList, matchConstraintOutcomeList, equivalenceRelations, M);
		
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
		
		Iterator<List<SyllableToken>> A = buildEfficiently(matchConstraintList, matchConstraintOutcomeList, equivalenceRelations, M);
		
		while(A.hasNext()) {
			System.out.println(A.next());
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
		
		Iterator<List<CharacterToken>> A = buildEfficiently(matchConstraintList, matchConstraintOutcomeList, M);
		
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
		
		Iterator<List<CharacterToken>> A = buildEfficiently(matchConstraintList, matchConstraintOutcomeList, M);
		
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
		
		Iterator<List<CharacterToken>> A = buildEfficiently(matchConstraintList, matchConstraintOutcomeList, M, controlConstraints);
		
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
		
		Iterator<List<SyllableToken>> A = buildEfficiently(matchConstraintList, matchConstraintOutcomeList, equivalenceRelations, M);
		
		while(A.hasNext()) {
			System.out.println(A.next());
		}
	}
}
