package automaton;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang3.time.StopWatch;

import java.util.Set;
import java.util.SortedSet;
import java.util.Stack;
import java.util.TreeSet;

import automaton.RegularConstraintApplier.StateToken;
import dbtb.constraint.ConditionedConstraint;
import dbtb.constraint.Constraint;
import dbtb.constraint.StateConstraint;
import dbtb.data.DataLoader;
import dbtb.data.SyllableToken;
import dbtb.markov.BidirectionalVariableOrderPrefixIDMap;
import dbtb.markov.SparseVariableOrderMarkovModel;
import dbtb.markov.SparseVariableOrderMarkovModel.CharacterToken;
import dbtb.markov.SparseVariableOrderMarkovModel.CharacterToken.CharacterTokenConstraint;
import dbtb.markov.SparseVariableOrderNHMMMultiThreaded;
import dbtb.markov.Token;
import dbtb.markov.UnsatisfiableConstraintSetException;
import dbtb.utils.Pair;
import dbtb.utils.Triple;
import dbtb.utils.Utils;

public class MatchDFABuilderDFS {

	public static <T extends Token> Automaton<T> buildEfficiently(int[] matchConstraintList, boolean[] matchConstraintOutcomeList, SparseVariableOrderMarkovModel<T> markovModel) {
		List<List<ConditionedConstraint<T>>> controlConstraints = new ArrayList<List<ConditionedConstraint<T>>>();
		
		for (int i = 0; i < matchConstraintList.length; i++) {
			controlConstraints.add(new ArrayList<ConditionedConstraint<T>>());
		}
		
		return buildEfficiently(matchConstraintList, matchConstraintOutcomeList, markovModel, controlConstraints);
	}

	public static <T extends Token> Automaton<T> buildEfficiently(int[] matchConstraintList, boolean[] matchConstraintOutcomeList, SparseVariableOrderMarkovModel<T> markovModel, List<List<ConditionedConstraint<T>>> controlConstraints) {
		int[][] newMatchConstraintList = new int[1][];
		newMatchConstraintList[0] = matchConstraintList;
		boolean[][] newMatchConstraintOutcomeList = new boolean[1][];
		newMatchConstraintOutcomeList[0] = matchConstraintOutcomeList;
		return buildEfficiently(newMatchConstraintList, newMatchConstraintOutcomeList, null, markovModel, controlConstraints);
	}
	
	
	public static <T extends Token> Automaton<T> buildEfficiently(int[] matchConstraintList, boolean[] matchConstraintOutcomeList, 
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

	public static <T extends Token> Automaton<T> buildEfficiently(int[][] matchConstraintList, boolean[][] matchConstraintOutcomeList, 
			List<Comparator<T>> equivalenceRelations, SparseVariableOrderMarkovModel<T> markovModel) {
		List<List<ConditionedConstraint<T>>> controlConstraints = new ArrayList<List<ConditionedConstraint<T>>>();
		
		for (int i = 0; i < matchConstraintList[0].length; i++) {
			controlConstraints.add(new ArrayList<ConditionedConstraint<T>>());
		}
		return buildEfficiently(matchConstraintList, matchConstraintOutcomeList, equivalenceRelations, markovModel, controlConstraints);
	}
	
	public static <T extends Token> Automaton<T> buildEfficiently(int[][] matchConstraintList, boolean[][] matchConstraintOutcomeList, List<Comparator<T>> equivalenceRelations, SparseVariableOrderMarkovModel<T> markovModel, List<List<ConditionedConstraint<T>>> controlConstraints) {
		Map<Integer, Map<Integer, Integer>> delta = new HashMap<Integer, Map<Integer, Integer>>();
		Map<Integer,Map<Integer, SortedSet<Integer>>> invDelta = new HashMap<Integer, Map<Integer, SortedSet<Integer>>>();
		Set<Integer> acceptingStates = new HashSet<Integer>();
		
		nextStateID = 1;
		
		int[][] dfsMatchConstraintList = new int[matchConstraintList.length][];
		boolean[][] dfsMatchConstraintOutcomeList = new boolean[matchConstraintOutcomeList.length][];
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
//		System.out.println("matchConstraintList:" + Arrays.toString(matchConstraintList[0]));
//		System.out.println("matchConstraintOutcomeList:" + Arrays.toString(matchConstraintOutcomeList[0]));
//		System.out.println("dfsMatchConstraintList:" + Arrays.toString(dfsMatchConstraintList[0]));
//		System.out.println("dfsMatchConstraintOutcomeList:" + Arrays.toString(dfsMatchConstraintOutcomeList[0]));

		int[][] equivalenceClassMap = computeEquivalenceClasses(markovModel.stateIndex, equivalenceRelations, matchConstraintList.length);

		// 0-based depth and Markov state
		Stack<Pair<Integer,Integer>> visitStack = new Stack<Pair<Integer,Integer>>();
		Stack<Integer> pathStack = new Stack<Integer>();
		Stack<Integer> currentPathStateStack = new Stack<Integer>();
		
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
		
		Map<Integer, Map<Integer, Double>> validMarkovTransitions = markovModel.logTransitions;
		
		Integer currentDepth, currentMarkovState;
		Pair<Integer, Integer> currentDepthAndState;
		int equivalenceClassAtMatchPos;
		
		StopWatch watch = new StopWatch();
		watch.start();
		int solutions = 0;
		
		while(!visitStack.isEmpty()) {
			currentDepthAndState = visitStack.pop();
//			System.out.println("Popped Depth and State: " + currentDepthAndState);
			currentDepth = currentDepthAndState.getFirst();
			final int nextDepth = currentDepth+1;
			currentMarkovState = currentDepthAndState.getSecond();
			
			//pop visited path to get to currentDepth
			while(pathStack.size() > currentDepth) {
				pathStack.pop();
			}
			
			pathStack.push(currentMarkovState);
//			System.out.println("visitStack: " + visitStack);
//			System.out.println("pathStack: " + pathStack);
			final Map<Integer, Double> validMarkovTransitionsFromLabel = validMarkovTransitions.get(currentMarkovState);
			
			controlConstraintsAti = controlConstraints.get(nextDepth);
			final List<Integer> nextStates = validMarkovTransitionsFromLabel == null?new ArrayList<Integer>():new ArrayList<Integer>(validMarkovTransitionsFromLabel.keySet());
			Collections.shuffle(nextStates);
			for (Integer validMarkovTransition : nextStates) {
				boolean keep = true;
				//control constraints
				for (ConditionedConstraint<T> conditionedConstraint : controlConstraintsAti) {
//					System.out.println("Checking control constraint");
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
//						System.out.println("Checking match constraint");
						equivalenceClassAtMatchPos = equivalenceClassMap[j][pathStack.elementAt(dfsMatchConstraintList[j][nextDepth])];
						if ((equivalenceClassAtMatchPos == equivalenceClassMap[j][validMarkovTransition]) != dfsMatchConstraintOutcomeList[j][nextDepth]) {
//							System.out.println("" + dfsMatchConstraintList[j][nextDepth] + "≠" + nextDepth);
							keep = false;
							break;
						}
					}
				}
				if (keep) {
					final Pair<Integer, Integer> nextDepthAndState = new Pair<Integer,Integer>(nextDepth, validMarkovTransition);
//					System.out.println("Checking if solution is of len " + matchConstraintList[0].length);
					if (nextDepth+1 == matchConstraintList[0].length) {
						pathStack.push(validMarkovTransition);
						System.out.println("Solution found: " + pathStack);
						solutions++;
						// Each time a solution is found it is folded into the DFA (keep a reverse delta)
//						addSolutionToDFA(pathStack,delta,invDelta);
						
						// add the solution to the DFA
						for (int i = currentPathStateStack.size(); i <= pathStack.size(); i++) {
							
						}
						currentPathStateStack
//						return null;
					} else {
//						System.out.println("Pushing " + nextDepthAndState);
						visitStack.push(nextDepthAndState);
					}
				}
			}
			
			if (computePercentTotalMemoryUsed() > 40 || watch.getTime() > 20000) {
				System.out.println("Time or memory limit reached");
				break;
			}
		}
		
		if (delta.size() == 0) {
			throw new RuntimeException("Unsatisfiable");
		}
		
		System.out.println("" + solutions + " solutions found via DFS");
		
		acceptingStates.add(Integer.MAX_VALUE);
		
		//Before returning (or when memory becomes an issue) the DFA it needs to be condensed by looking for identical states (same outgoing edge set) at each layer 
		return new Automaton<T>(markovModel.stateIndex,delta,acceptingStates);
	}

	
	public static double computePercentTotalMemoryUsed() {
		return (100.0*(Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()))/Runtime.getRuntime().maxMemory();
	}
	
	private static int nextStateID;
	private static void addSolutionToDFA(Stack<Integer> pathStack, Map<Integer, Map<Integer, Integer>> delta,
			Map<Integer, Map<Integer, SortedSet<Integer>>> invDelta) {
		//start at state 0 and go as far forward as possible via the labels in pathStack to find the front
		Integer frontState = 0;
		Map<Integer, Integer> frontStateMap;
		Integer nextFrontState = null;
		int frontStatePos = 0;
		
		while ((frontStateMap = delta.get(frontState)) != null && (nextFrontState = frontStateMap.get(pathStack.elementAt(frontStatePos))) != null) {
			frontState = nextFrontState;
			frontStatePos++;
//			System.out.println("Existing front state " + frontState + " at position " + frontStatePos);
		}
		if (frontStateMap == null) {
			frontStateMap = new HashMap<Integer, Integer>();
			delta.put(frontState, frontStateMap);
		}
		
		//start at accept state and go as far backward as possible via the labels in pathStack to find the back (but not further back than the front)
		Integer rearState = Integer.MAX_VALUE;
		Map<Integer, SortedSet<Integer>> rearStateMap = invDelta.get(rearState);
		SortedSet<Integer> nextRearStates = null;
		int rearStatePos = pathStack.size()-1;
		
//		while ((rearStateMap = invDelta.get(rearState)) != null && rearStatePos > frontStatePos && (nextRearStates = rearStateMap.get(pathStack.elementAt(rearStatePos))) != null) {
//			rearState = nextRearStates.first();
//			System.out.println("Existing rear state " + rearState + " at position " + rearStatePos);
//			rearStatePos--;
//		}
		if (rearStateMap == null) {
			rearStateMap = new HashMap<Integer, SortedSet<Integer>>();
			invDelta.put(rearState, rearStateMap);
			nextRearStates = null; //clear pointer
		}  else {//else if (rearStatePos == frontStatePos){
			nextRearStates = rearStateMap.get(pathStack.elementAt(rearStatePos));
		}
		
		//create path from frontState to rearState
		HashMap<Integer, SortedSet<Integer>> tmpRearStateMap;
		Integer nextLabel;
		while(rearStatePos > frontStatePos) {
			nextLabel = pathStack.elementAt(frontStatePos);
			frontStateMap.put(nextLabel, nextStateID);
			frontStateMap = new HashMap<Integer, Integer>();
			delta.put(nextStateID, frontStateMap);

			tmpRearStateMap = new HashMap<Integer, SortedSet<Integer>>();
			tmpRearStateMap.put(nextLabel, new TreeSet<Integer>(Arrays.asList(frontState)));
			invDelta.put(nextStateID, tmpRearStateMap);
			frontState = nextStateID;
			frontStatePos++;
			nextStateID++;
//			System.out.println("New front state " + frontState + " at position " + frontStatePos);
		}
		
		nextLabel = pathStack.elementAt(frontStatePos);
		frontStateMap.put(nextLabel, rearState);
		if (nextRearStates == null) {
			nextRearStates = new TreeSet<Integer>(Arrays.asList(frontState)); 
			rearStateMap.put(nextLabel, nextRearStates);
		} else {
			nextRearStates.add(frontState);
		}
//		System.out.println("Connecting " + frontState + " and " + rearState);
//		System.out.println(invDelta);
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
//		runExample1();
//		runExample2(); // test for combining states
//		runExample3();
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
		Automaton<SyllableToken> A = buildEfficiently(matchConstraintList, matchConstraintOutcomeList, equivalenceRelations, M);
		
		final ArrayList<List<ConditionedConstraint<StateToken<SyllableToken>>>> constraints = new ArrayList<List<ConditionedConstraint<StateToken<SyllableToken>>>>();
		for (int i = 0; i < length; i++) {
			constraints.add(new ArrayList<ConditionedConstraint<StateToken<SyllableToken>>>());
		}
		
		SparseVariableOrderNHMMMultiThreaded<StateToken<SyllableToken>> NHMM = RegularConstraintApplier.combineAutomataWithMarkov(M, A, length, constraints);
		System.out.println("NHMM:");
		for (int i = 0; i < 20; i++) {
			final List<StateToken<SyllableToken>> generate = NHMM.generate(length);
			System.out.println("\t\t" + generate + "\tProb:" + NHMM.probabilityOfSequence(generate.toArray(new Token[0])));
		}
		
		FactorGraph<SyllableToken> factorGraph = RegularConstraintApplier.combineAutomataWithMarkovInFactorGraph(M, A, length, constraints);
		System.out.println("Factor Graph:");
		for (int i = 0; i < 20; i++) {
			final Pair<List<StateToken<SyllableToken>>,Double> generate = factorGraph.generate(length);
			System.out.println("\t\t" + generate.getFirst() + "\tProb:" + generate.getSecond());
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
		
		Automaton<SyllableToken> A = buildEfficiently(matchConstraintList, matchConstraintOutcomeList, equivalenceRelations, M);
		
		final ArrayList<List<ConditionedConstraint<StateToken<SyllableToken>>>> constraints = new ArrayList<List<ConditionedConstraint<StateToken<SyllableToken>>>>();
		for (int i = 0; i < length; i++) {
			constraints.add(new ArrayList<ConditionedConstraint<StateToken<SyllableToken>>>());
		}
		
		SparseVariableOrderNHMMMultiThreaded<StateToken<SyllableToken>> NHMM = RegularConstraintApplier.combineAutomataWithMarkov(M, A, length, constraints);
		System.out.println("NHMM:");
		for (int i = 0; i < 20; i++) {
			final List<StateToken<SyllableToken>> generate = NHMM.generate(length);
			System.out.println("\t\t" + generate + "\tProb:" + NHMM.probabilityOfSequence(generate.toArray(new Token[0])));
		}
		
		FactorGraph<SyllableToken> factorGraph = RegularConstraintApplier.combineAutomataWithMarkovInFactorGraph(M, A, length, constraints);
		System.out.println("Factor Graph:");
		for (int i = 0; i < 20; i++) {
			final Pair<List<StateToken<SyllableToken>>,Double> generate = factorGraph.generate(length);
			System.out.println("\t\t" + generate.getFirst() + "\tProb:" + generate.getSecond());
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
		
		Automaton<SyllableToken> A = buildEfficiently(matchConstraintList, matchConstraintOutcomeList, equivalenceRelations, M);
		
		final ArrayList<List<ConditionedConstraint<StateToken<SyllableToken>>>> constraints = new ArrayList<List<ConditionedConstraint<StateToken<SyllableToken>>>>();
		for (int i = 0; i < length; i++) {
			constraints.add(new ArrayList<ConditionedConstraint<StateToken<SyllableToken>>>());
		}
		
		SparseVariableOrderNHMMMultiThreaded<StateToken<SyllableToken>> NHMM = RegularConstraintApplier.combineAutomataWithMarkov(M, A, length, constraints);
		System.out.println("NHMM:");
		for (int i = 0; i < 20; i++) {
			final List<StateToken<SyllableToken>> generate = NHMM.generate(length);
			System.out.println("\t\t" + generate + "\tProb:" + NHMM.probabilityOfSequence(generate.toArray(new Token[0])));
		}
		
		FactorGraph<SyllableToken> factorGraph = RegularConstraintApplier.combineAutomataWithMarkovInFactorGraph(M, A, length, constraints);
		System.out.println("Factor Graph:");
		for (int i = 0; i < 20; i++) {
			final Pair<List<StateToken<SyllableToken>>,Double> generate = factorGraph.generate(length);
			System.out.println("\t\t" + generate.getFirst() + "\tProb:" + generate.getSecond());
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
		
		Automaton<CharacterToken> A = buildEfficiently(matchConstraintList, matchConstraintOutcomeList, M);
		
		System.out.println("A.sigma:");
		System.out.println(A.sigma.getIDToPrefixMap());
		System.out.println("A.delta:");
		System.out.println(A.delta);
		System.out.println("A.acceptingStates:");
		System.out.println(A.acceptingStates);
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
		
		Automaton<CharacterToken> A = buildEfficiently(matchConstraintList, matchConstraintOutcomeList, M);
		
		System.out.println("A.sigma:");
		System.out.println(A.sigma.getIDToPrefixMap());
		System.out.println("A.delta:");
		System.out.println(A.delta);
		System.out.println("A.acceptingStates:");
		System.out.println(A.acceptingStates);
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
		
		Automaton<CharacterToken> A = buildEfficiently(matchConstraintList, matchConstraintOutcomeList, M, controlConstraints);
		
		System.out.println("A.sigma:");
		System.out.println(A.sigma.getIDToPrefixMap());
		System.out.println("A.delta:");
		System.out.println(A.delta);
		System.out.println("A.acceptingStates:");
		System.out.println(A.acceptingStates);
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
		int length = matchConstraintList[0].length;
		
		List<Comparator<SyllableToken>> equivalenceRelations = new ArrayList<Comparator<SyllableToken>>(){{
			add(null);
			add(new RhymeComparator());
		}};
		
		boolean[][] matchConstraintOutcomeList = new boolean[2][matchConstraintList[0].length];
		for (boolean[] bs : matchConstraintOutcomeList) {
			Arrays.fill(bs, true);
		}
		
		Automaton<SyllableToken> A = buildEfficiently(matchConstraintList, matchConstraintOutcomeList, equivalenceRelations, M);
		
		System.out.println("A.sigma:");
		System.out.println(A.sigma.getIDToPrefixMap());
		System.out.println("A.delta:");
		System.out.println(A.delta);
		System.out.println("A.acceptingStates:");
		System.out.println(A.acceptingStates);
		
		final ArrayList<List<ConditionedConstraint<StateToken<SyllableToken>>>> constraints = new ArrayList<List<ConditionedConstraint<StateToken<SyllableToken>>>>();
		for (int i = 0; i < length; i++) {
			constraints.add(new ArrayList<ConditionedConstraint<StateToken<SyllableToken>>>());
		}
		
		SparseVariableOrderNHMMMultiThreaded<StateToken<SyllableToken>> NHMM = RegularConstraintApplier.combineAutomataWithMarkov(M, A, length, constraints);
		System.out.println("NHMM:");
		for (int i = 0; i < 20; i++) {
			final List<StateToken<SyllableToken>> generate = NHMM.generate(length);
			System.out.println("\t\t" + generate + "\tProb:" + NHMM.probabilityOfSequence(generate.toArray(new Token[0])));
		}
		
		FactorGraph<SyllableToken> factorGraph = RegularConstraintApplier.combineAutomataWithMarkovInFactorGraph(M, A, length, constraints);
		System.out.println("Factor Graph:");
		for (int i = 0; i < 20; i++) {
			final Pair<List<StateToken<SyllableToken>>,Double> generate = factorGraph.generate(length);
			System.out.println("\t\t" + generate.getFirst() + "\tProb:" + generate.getSecond());
		}
	}
}
