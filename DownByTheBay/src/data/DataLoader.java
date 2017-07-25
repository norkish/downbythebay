package data;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.time.StopWatch;

import edu.stanford.nlp.util.CoreMap;
import linguistic.phonetic.Phoneme;
import linguistic.phonetic.Phoneticizer;
import linguistic.phonetic.VowelPhoneme;
import linguistic.phonetic.syllabic.WordSyllables;
import linguistic.syntactic.Pos;
import linguistic.syntactic.StanfordNlpInterface;
import main.Main;
import markov.BidirectionalVariableOrderPrefixIDMap;
import markov.Token;
import utils.Pair;
import utils.Utils;

public class DataLoader {
	
//	private static final long MAX_TRAINING_SENTENCES = -1; // -1 for no limit - CAN'T USE THIS TO THROTTLE ANY MORE - requires syncing
	private static final long MAX_TOKENS_PER_SENTENCE = 30; // keeps Stanford NLP fast
	private static final int NUM_THREADS = Runtime.getRuntime().availableProcessors()-1;
	private static final int DEBUG = 1; 
	private static final double MAX_MEMORY_FOR_BASE_MODEL = 50.;
	final private int BATCH_SIZE = 1000;
	
	private static final boolean USE_DUMMY_DATA = false; 
	private String[] TRAINING = new String[]{
			"iced cakes inside The Bake",
			"Have you seen a moose with a pair of new shoes?",
			"Have you ever seen a bear combing his hair?",
			"Have you ever seen a llama wearing polka dot pajamas?",
			"Have you ever seen a llama wearing pajamas?",
			"Have you ever seen a moose with a pair of new shoes?",
			"Have you ever seen a pirate that just ate a veggie diet?",
			"Have you ever seen the law drinking from a straw?"
//			"I'm a bear combin' his hair?",
//			"Why is it so weird to think about a llama wearing polka dot pajamas?",
//			"I have a llama wearing pajamas.",
//			"Have you a pirate that just ate a veggie diet?",
	};
	
	
	public class DataProcessor {

		private String[] trainingSentences;
		private int nextBatchIdx;
		
		public DataProcessor(String[] trainingSentences) {
			this.trainingSentences = trainingSentences;
			nextBatchIdx = 0;
		}
		
		private synchronized int getNextBatchStart() {
			int nextBatchStart = nextBatchIdx;
			nextBatchIdx += BATCH_SIZE;
			return nextBatchStart;
		}
		
		boolean threadCurrentlySyncing = false;
		private synchronized boolean threadCurrentlySyncing() {
			if (threadCurrentlySyncing) {
				return true;
			} else {
				threadCurrentlySyncing = true;
				return false;
			}
		}

		int returnStatus = 0;
		public int process() throws InterruptedException {
			System.out.println("Starting " + NUM_THREADS + " threads");
			System.gc();
			StopWatch watch = new StopWatch();
			
			watch.start();
			//start threads
			ThreadGroup tg = new ThreadGroup("all threads");
			for (int j = 0; j < NUM_THREADS; j++) {
				Thread t = new Thread(tg, new Runnable() {
					
					@Override
					public void run() {
						
						StopWatch stepTimer = new StopWatch();
						int watch1Time = 0;
						int watch2Time = 0;
						int watch3Time = 0;
						int watch4Time = 0;
						int watch5Time = 0;
						int watch6Time = 0;
						int watch7Time = 0;
						int watch8Time = 0;
						int watch1Count = 0;
						int watch2Count = 0;
						int watch3Count = 0;
						int watch4Count = 0;
						int watch5Count = 0;
						int watch6Count = 0;
						int watch7Count = 0;
						int watch8Count = 0;
						
						Map<Integer, Double> priorCountsForBatch = new HashMap<Integer, Double>();
						Map<Integer, Map<Integer, Double>> transitionCountsForBatch = new HashMap<Integer, Map<Integer, Double>>();
						BidirectionalVariableOrderPrefixIDMap<SyllableToken> prefixIDMapForBatch = new BidirectionalVariableOrderPrefixIDMap<SyllableToken>(order);

						int sentencePronunciationsTrainedOnForBatch = 0;
						int sentencesTrainedOnForBatch = 0;
						
						int nextBatchStart = getNextBatchStart();

						int nextBatchEnd;
						boolean threadReadyToSync = false;
						
						while((!threadReadyToSync || threadCurrentlySyncing()) && nextBatchStart < trainingSentences.length) {
							nextBatchEnd = Math.min(nextBatchStart+BATCH_SIZE, trainingSentences.length);
							System.out.println(Thread.currentThread().getName() + " training on sentences " + nextBatchStart + " to " + (nextBatchEnd-1) + " (MEM:" + Main.computePercentTotalMemoryUsed() + "%)");
							for (int i = nextBatchStart; i < nextBatchEnd; i++) {
								stepTimer.start();
								String trainingSentence = trainingSentences[i];
								stepTimer.stop();
								watch1Time += stepTimer.getTime();
								watch1Count++;
								stepTimer.reset();
	//							System.out.println("Training on "+trainingSentence);
								//start thread
								// get syllable tokens for all unique pronunciations of the sentence
								stepTimer.start();
								List<List<SyllableToken>> trainingTokensSentences = convertToSyllableTokens(cleanSentence(trainingSentence));
								stepTimer.stop();
								watch3Time += stepTimer.getTime();
								watch3Count++;
								stepTimer.reset();
								// if there was no valid pronunciation, skip it
								if (trainingTokensSentences == null) continue;
								stepTimer.start();
								// for each pronunciation
								if (DEBUG > 1) System.out.println("PRON COUNT:" + trainingTokensSentences.size());
								final double trainingWeight = 1.0/trainingTokensSentences.size();
								// Start synchronization
								Integer fromTokenID, toTokenID;
								int sentencePronunciationsTrainedOnForSentence = 0;
								stepTimer.stop();
								watch4Time += stepTimer.getTime();
								watch4Count++;
								stepTimer.reset();
								stepTimer.start();
								for (List<SyllableToken> trainingSentenceTokens : trainingTokensSentences) {
									if (trainingSentenceTokens.size() < order) continue;
	//								LinkedList<Token> prefix = new LinkedList<Token>(Collections.nCopies(order, Token.getStartToken()));
									LinkedList<Token> prefix = new LinkedList<Token>(trainingSentenceTokens.subList(0, order));
									//TODO add string associated w/ prefix to set for Word2Vec
									fromTokenID = prefixIDMapForBatch.addPrefix(prefix);
									for (int j = order; j < trainingSentenceTokens.size(); j++ ) {
										prefix.removeFirst();
										prefix.addLast(trainingSentenceTokens.get(j));
										
										toTokenID = prefixIDMapForBatch.addPrefix(prefix);
										Utils.incrementValueForKeys(transitionCountsForBatch, fromTokenID, toTokenID, trainingWeight);
										Utils.incrementValueForKey(priorCountsForBatch, fromTokenID, trainingWeight); // we do this for every token 
	
										fromTokenID = toTokenID;
									}
									sentencePronunciationsTrainedOnForSentence++;
								}
								stepTimer.stop();
								watch5Time += stepTimer.getTime();
								watch5Count++;
								stepTimer.reset();
								stepTimer.start();
								if (DEBUG > 1) System.out.println("sentencesTrainedOn:" + sentencesTrainedOn + ", sentencePronunciationsTrainedOn:" + sentencePronunciationsTrainedOn + " transitions.size()=" + transitions.size() + " prefixIDMap.getPrefixCount()=" + prefixIDMapForBatch.getPrefixCount());
								
								if (sentencePronunciationsTrainedOnForSentence > 0){
									sentencesTrainedOnForBatch++;
									sentencePronunciationsTrainedOnForBatch += sentencePronunciationsTrainedOnForSentence;
								}
								stepTimer.stop();
								watch6Time += stepTimer.getTime();
								watch6Count++;
								stepTimer.reset();
	//							if (sentencesTrainedOnForBatch == 100) { // REMOVED TO AVOID SYNCRONIZATION REQUIREMENT
	//								incrementSentencesAndPronunciationsTrainedOn(sentencesTrainedOnForBatch, sentencePronunciationsTrainedOnForBatch);
	//								sentencesTrainedOnForBatch = 0;
	//								sentencePronunciationsTrainedOnForBatch = 0;
	//							}
								// End synchronization	
							}
							
							stepTimer.start();
							if (returnStatus != 1 && Main.computePercentTotalMemoryUsed() > MAX_MEMORY_FOR_BASE_MODEL) {
								System.out.println("Hit memory threshold of " + MAX_MEMORY_FOR_BASE_MODEL + " for base model training");
								returnStatus = 1;
							}
							if (returnStatus == 1) {
								threadReadyToSync = true;
							}
							stepTimer.stop();
							watch2Time += stepTimer.getTime();
							watch2Count++;
							stepTimer.reset();
							
							nextBatchStart = getNextBatchStart();
						}
						System.out.println(Thread.currentThread().getName() + " finished training");
						stepTimer.start();
						incrementSentencesAndPronunciationsTrainedOn(sentencesTrainedOnForBatch, sentencePronunciationsTrainedOnForBatch);
						stepTimer.stop();
						watch7Time += stepTimer.getTime();
						watch7Count++;
						stepTimer.reset();
						stepTimer.start();
						incrementTransitionsAndPriors(prefixIDMapForBatch, transitionCountsForBatch, priorCountsForBatch);
						stepTimer.stop();
						watch8Time += stepTimer.getTime();
						watch8Count++;
						stepTimer.reset();
//						System.out.println("Ending thread");
						
						System.out.println("ThreadName\tTotalWatchTimePerThread\tTotalWatchCountPerThread\tAvgWatchTimePerSentencePerThread\n" +
								Thread.currentThread().getName() +"\t" + watch1Time+"\t"+watch1Count+"\t" + ((1.0*watch1Time)/watch1Count) + "\n" +
								Thread.currentThread().getName() +"\t" + watch2Time+"\t"+watch2Count+"\t" + ((1.0*watch2Time)/watch2Count) + "\n" +
								Thread.currentThread().getName() +"\t" + watch3Time+"\t"+watch3Count+"\t" + ((1.0*watch3Time)/watch3Count) + "\n" +
								Thread.currentThread().getName() +"\t" + watch4Time+"\t"+watch4Count+"\t" + ((1.0*watch4Time)/watch4Count) + "\n" +
								Thread.currentThread().getName() +"\t" + watch5Time+"\t"+watch5Count+"\t" + ((1.0*watch5Time)/watch5Count) + "\n" +
								Thread.currentThread().getName() +"\t" + watch6Time+"\t"+watch6Count+"\t" + ((1.0*watch6Time)/watch6Count) + "\n" +
								Thread.currentThread().getName() +"\t" + watch7Time+"\t"+watch7Count+"\t" + ((1.0*watch7Time)/watch7Count) + "\n" +
								Thread.currentThread().getName() +"\t" + watch8Time+"\t"+watch8Count+"\t" + ((1.0*watch8Time)/watch8Count));
						
					}

				});
//				System.out.println("Starting new thread");
				t.start();
			}
			
			int activeThreadCount = tg.activeCount()*2;
			Thread[] threads = new Thread[activeThreadCount];
			tg.enumerate(threads);
			for (int j = 0; j < activeThreadCount; j++) {
				Thread t = threads[j];
				if (t!=null) {
					t.join();
				}
			}
			
			
			watch.stop();
			System.out.println("Time training with " + NUM_THREADS + " threads: " + watch.getTime());
			System.gc();

			return returnStatus; // if 1 is returned, it means the file wasn't fully read.
		}
		private synchronized void incrementTransitionsAndPriors(
				BidirectionalVariableOrderPrefixIDMap<SyllableToken> prefixIDMapForBatch, Map<Integer, Map<Integer, Double>> transitionCountsForBatch,
				Map<Integer, Double> priorCountsForBatch) {
			
			System.out.println("Synchronizing batch transitions and priors for thread " + Thread.currentThread().getName() + "... ");
			
			if (prefixIDMap.isEmpty()) { // if empty, just adopt this batch's model
				prefixIDMap = prefixIDMapForBatch;
				transitions = transitionCountsForBatch;
				priors = priorCountsForBatch;
			} else {
				Double prevCount, batchCount;
				LinkedList<Token> fromState, toState;
				Integer absoluteFromID, absoluteToID;
				Map<Integer, Double> batchTransitionsMap, aggregateTransitionsMap;
				
				// otherwise, iterate over the transitions for the batch
				for (Integer fromID : transitionCountsForBatch.keySet()) {
					batchTransitionsMap = transitionCountsForBatch.get(fromID);
					
					// get the state intended by the batch's from ID
					fromState = prefixIDMapForBatch.getPrefixForID(fromID);
					// translate that state to the ID assigned to that state in the aggregate prefixIDMap
					absoluteFromID = prefixIDMap.addPrefix(fromState);
					// lookup the transitions map in the aggregate model using that absolute ID
					aggregateTransitionsMap = transitions.get(absoluteFromID);
					
					// if no transitions exist for that from state (ID)
					if (aggregateTransitionsMap == null) {
						// initialize it and populate it (Can't copy because we have to translate all toIDs)
						aggregateTransitionsMap = new HashMap<Integer, Double>();
						transitions.put(absoluteFromID, aggregateTransitionsMap);
					} 
					
					// for each batch toID
					for(Integer toID : batchTransitionsMap.keySet()) {
						batchCount = batchTransitionsMap.get(toID);
						
						// get the state represented by the batch ID
						toState = prefixIDMapForBatch.getPrefixForID(toID);
						// lookup the absolute id from the aggregate prefixid map
						absoluteToID = prefixIDMap.addPrefix(toState);
						// lookup the previous account associated with this state (ID)
						prevCount = aggregateTransitionsMap.get(absoluteToID);
						
						// if no previous counts
						if (prevCount == null) {
							// just copy the count
							aggregateTransitionsMap.put(absoluteToID, batchCount);
						} else {
							// otherwise sum
							aggregateTransitionsMap.put(absoluteToID, prevCount + batchCount);
						}
					}
				}
				
				// for each batch id in the prior dataset
				for(Integer id : priorCountsForBatch.keySet()) {
					batchCount = priorCountsForBatch.get(id);
					
					// lookup the state represented by that id
					toState = prefixIDMapForBatch.getPrefixForID(id);
					// find the absolute ID from the aggregate prefixIDMap
					absoluteToID = prefixIDMap.addPrefix(toState);
					// get the prevCounts associated with this state (ID)
					prevCount = priors.get(absoluteToID);
					
					// if no previous counts
					if (prevCount == null) {
						// just copy the count
						priors.put(absoluteToID, batchCount);
					} else {
						// otherwise sum
						priors.put(absoluteToID, prevCount + batchCount);
					}
				}
			}
			threadCurrentlySyncing = false;
			System.out.println("Synchronization Complete!");
		}
	}
	
	/**
	 * Assumes global ids
	 * @param transitionCountsForBatch
	 * @param priorCountsForBatch
	 */
	private synchronized void incrementTransitionsAndPriors(Map<Integer, Map<Integer, Double>> transitionCountsForBatch,
			Map<Integer, Double> priorCountsForBatch) {

//		System.out.println("Synchronizing batch transitions and priors");

		
		Double prevCount, batchCount;
		Map<Integer, Double> batchTransitionsMap, aggregateTransitionsMap;
		
		// otherwise, iterate over the transitions for the batch
		for (Integer fromID : transitionCountsForBatch.keySet()) {
			batchTransitionsMap = transitionCountsForBatch.get(fromID);

			// lookup the transitions map in the aggregate model using that ID
			aggregateTransitionsMap = transitions.get(fromID);
			
			// if no transitions exist for that from state (ID)
			if (aggregateTransitionsMap == null) {
				transitions.put(fromID, batchTransitionsMap);
			} else { 
				// for each batch toID
				for(Integer toID : batchTransitionsMap.keySet()) {
					batchCount = batchTransitionsMap.get(toID);
	
					// lookup the previous account associated with this state (ID)
					prevCount = aggregateTransitionsMap.get(toID);
					
					// if no previous counts
					if (prevCount == null) {
						// just copy the count
						aggregateTransitionsMap.put(toID, batchCount);
					} else {
						// otherwise sum
						aggregateTransitionsMap.put(toID, prevCount + batchCount);
					}
				}
			}
		}
		
		// for each batch id in the prior dataset
		for(Integer id : priorCountsForBatch.keySet()) {
			batchCount = priorCountsForBatch.get(id);

			// get the prevCounts associated with this state (ID)
			prevCount = priors.get(id);
			
			// if no previous counts
			if (prevCount == null) {
				// just copy the count
				priors.put(id, batchCount);
			} else {
				// otherwise sum
				priors.put(id, prevCount + batchCount);
			}
		}
	}

	public static StanfordNlpInterface nlp = new StanfordNlpInterface();

	public static class DataSummary {
		/**
		 * This map serves to store all possible tokens seen in the data set. Representing
		 * each prefix as an integer saves time and space later on. Essentially each new token (after
		 * checking it is not already in the map) is added thus: int tokenID = statesByIndex.addPrefix(prefix), 
		 * where prefix is an object of type LinkedList<SyllableToken>.
		 * The integer value in the map is what is used as the key for both the priors and the transitions data
		 * structures.
		 */
		public BidirectionalVariableOrderPrefixIDMap<SyllableToken> statesByIndex;
		
		/**
		 * The inner key k_1 and outer key k_2 are the prefix IDs (see description of statesByIndex) for the from- and to-tokens respectively.
		 * The value is the transition probability of going from k_1 to k_2 as learned empirically from the data.
		 * Only insert inner and outer keys for non-zero transition probabilities (i.e., the absence
		 * of a key is used to indicate a probability of 0). k_1 and k_2 are both sequences of tokens of length markovOrder.
		 */
		public Map<Integer, Map<Integer, Double>> transitions;

		public Map<Integer, Double> priors;

		public DataSummary(BidirectionalVariableOrderPrefixIDMap<SyllableToken> statesByIndex, Map<Integer, Double> priors, Map<Integer, Map<Integer, Double>> transitions) {
			this.statesByIndex = statesByIndex;
			this.transitions = transitions;
			this.priors = priors;
		}
	}

	private int order;
	private BidirectionalVariableOrderPrefixIDMap<SyllableToken> prefixIDMap;
	private Map<Integer, Double> priors = new HashMap<Integer, Double>();
	private Map<Integer, Map<Integer, Double>> transitions = new HashMap<Integer, Map<Integer, Double>>();
	private long sentencesTrainedOn = 0;
	private long sentencePronunciationsTrainedOn = 0;
	
	public DataLoader(int markovOrder) {
		this.order = markovOrder;
		this.prefixIDMap = new BidirectionalVariableOrderPrefixIDMap<SyllableToken>(order);
	}

	public DataSummary loadData() throws InterruptedException {
		
		String[] trainingSentences;
		if (USE_DUMMY_DATA) {
			trainingSentences = TRAINING ;
		}
	
		for (int i = 1990; i <= 2012; i++) {
			if (!USE_DUMMY_DATA) {
				StringBuilder str = new StringBuilder();
				try {
//					final String fileName = "data/text_magazine_qrb/w_mag_" + i + ".txt";
					final String fileName = "data/text_fiction_awq/w_fic_" + i + ".txt";
					if (DEBUG > 0) System.out.println("Now training on " + fileName);
					BufferedReader br = new BufferedReader(new FileReader(fileName));
					String currLine;
					while ((currLine = br.readLine()) != null) {
						str.append(currLine);
					}
					br.close();
				} catch (Exception e) {
					e.printStackTrace();
				}
				
				String fileContents = str.toString();
				fileContents = fileContents.replaceAll("##\\d+(?= )", "");
				fileContents = fileContents.replaceAll("-## ", "");
				fileContents = fileContents.replaceAll("<p> ", "");
				trainingSentences = fileContents.split(" [[^a-zA-Z']+ ]+");
			}

			DataProcessor dp = new DataProcessor(trainingSentences);
			int status = dp.process();
			
			if (status == 1 || USE_DUMMY_DATA) {
				break;
			}
			System.err.println("Trained so far on (exactly) " + sentencesTrainedOn + " sentences, " + sentencePronunciationsTrainedOn + " sentence pronunciations");
		}
		Utils.normalizeByFirstDimension(transitions);
		
		DataSummary summary = new DataSummary(prefixIDMap, priors, transitions);
		return summary;
	}

	private synchronized void incrementSentencesAndPronunciationsTrainedOn(int sentencesTrainedOn, int sentencePronunciationsTrainedOn) {
		this.sentencesTrainedOn += sentencesTrainedOn;
		this.sentencePronunciationsTrainedOn += sentencePronunciationsTrainedOn;
	}

	final private static String[] suffixes = new String[]{" n't "," ' "," 's "," 've ", " 'd ", " 'll ", " 're ", " 't "," 'm "};
	private String cleanSentence(String trainingSentence) {
		if (DEBUG > 1) System.out.println("CLEAN:" + trainingSentence);
		trainingSentence = " " + trainingSentence + " ";
		for (String suffix : suffixes) {
			if (trainingSentence.contains(suffix)) {
				return null;
			}
//			trainingSentence = trainingSentence.replaceAll(suffix, suffix.substring(1));
		}
		
		trainingSentence = trainingSentence.trim();
		
		if (trainingSentence.isEmpty()) {
			return null;
		}
		
		final int length = trainingSentence.split("\\s+").length;
		if (length > MAX_TOKENS_PER_SENTENCE){// || length < order) {
			return null;
		}
		
		return trainingSentence;
	}

	private static List<List<SyllableToken>> convertToSyllableTokens(String trainingSentence) {
		if (DEBUG > 1) System.out.println("CONVERT:" + trainingSentence);
		if (trainingSentence == null || trainingSentence.trim().isEmpty()) return null;
		List<CoreMap> taggedSentences = nlp.parseTextToCoreMaps(trainingSentence);
		List<Pair<String,Pos>> taggedWords = nlp.parseCoreMapsToPairs(taggedSentences.get(0));
		//TODO deal with instances where Stanford tagger splits words, like "don't" -> "do" + "n't"
//		final String[] words = trainingSentence.split("\\s+");

		List<List<SyllableToken>> allTokensSentences = new ArrayList<>();
		allTokensSentences.add(new ArrayList<>());
		// for every word
		for (Pair<String,Pos> taggedWord : taggedWords) {
			if (taggedWord.getSecond() == null) continue;
			List<WordSyllables> pronunciations = Phoneticizer.syllableDict.get(taggedWord.getFirst().toUpperCase());
			if (pronunciations == null) {
				pronunciations = Phoneticizer.useG2P(taggedWord.getFirst().toUpperCase());
			}
			if (pronunciations == null || pronunciations.isEmpty()) return null;
//			reduceUnnecessaryPronunciations(pronunciations);
			// replicate all of the token sentences so far
			int pronunciationCount = pronunciations.size();
			replicateTokenSentences(allTokensSentences, pronunciationCount);
			
			// for every pronunciation of the word
			for (int i = 0; i < pronunciationCount; i++ ) {
				WordSyllables pronunciation = pronunciations.get(i); 
				// for each syllable in that pronunciation
				for (int j = 0; j < pronunciation.size(); j++) {
					// create a new syllable token
					final SyllableToken newSyllableToken = new SyllableToken(taggedWord.getFirst(), pronunciation.get(j).getPhonemeEnums(), taggedWord.getSecond(), pronunciation.size(), j, pronunciation.get(j).getStress());
					for (int k = i; k < allTokensSentences.size(); k+=pronunciationCount) {
						// and add it to each original sentence
						List<SyllableToken> sentenceTokens = allTokensSentences.get(k);
						sentenceTokens.add(newSyllableToken);
					}
				}
			}
		}
		// if there were no words
		return allTokensSentences;
	}

	/**
	 * Looks for instances where pronunciations are redundant. For example "with" can be stressed and unstressed.
	 * We want to keep the stressed version since in our stress constraint we care only that viable pronunciations
	 * have at least as great a stress as the constraint mandates
	 * @param pronunciations
	 */
	private static void reduceUnnecessaryPronunciations(List<WordSyllables> pronunciations) {
		Set<Integer> idxsToRemove = new HashSet<Integer>();
		List<Phoneme> wordSyllables1, wordSyllables2;
		for (int i = 0; i < pronunciations.size()-1; i++) {
			if (idxsToRemove.contains(i)) continue;
			wordSyllables1 = pronunciations.get(i).getPronunciation();
			for (int j = i+1; j < pronunciations.size(); j++) {
				if (idxsToRemove.contains(j)) continue;
				boolean allSamePhonemeEnums = true;
				boolean wordSyllable1hasLessStressedSyllables = false;
				boolean wordSyllable2hasLessStressedSyllables = false;
				wordSyllables2 = pronunciations.get(j).getPronunciation();
				if (wordSyllables1.size() == wordSyllables2.size()) {
					for (int k = 0; k < wordSyllables1.size(); k++) {
						Phoneme phoneme1 = wordSyllables1.get(k);
						Phoneme phoneme2 = wordSyllables2.get(k);
						if (phoneme1.getPhonemeEnum() == phoneme2.getPhonemeEnum()) {
							if (phoneme1.isVowel()) {
								if (phoneme2.isVowel()) {
									int phoneme1Stress = ((VowelPhoneme) phoneme1).stress;
									int phoneme2Stress = ((VowelPhoneme) phoneme2).stress;
									if (phoneme1Stress > phoneme2Stress) {
										wordSyllable2hasLessStressedSyllables = true;
									} else if (phoneme2Stress > phoneme1Stress) {
										wordSyllable1hasLessStressedSyllables = true;
									}
								}
							}
						} else {
							allSamePhonemeEnums = false;
							break;
						}
					}
				} else {
					allSamePhonemeEnums = false;
				}
				
				if (allSamePhonemeEnums) {
					if (wordSyllable1hasLessStressedSyllables) {
						if (!wordSyllable2hasLessStressedSyllables) {
							// they're all the same and wordSyllable2 is strictly more stressed, so we can get away with using just the first
							idxsToRemove.add(i);
						}
					} else {
						if (wordSyllable2hasLessStressedSyllables) {
							// they're all the same and wordSyllable1 is strictly more stressed, so we can get away with using just the second
							idxsToRemove.add(j);
						}
					}
				}
			}
		}
		
		final ArrayList<Integer> listOfIdxsToRemove = new ArrayList<Integer>(idxsToRemove);
		Collections.sort(listOfIdxsToRemove, Collections.reverseOrder());
		for (Integer idxToRemove : listOfIdxsToRemove) {
			pronunciations.remove((int)idxToRemove);
		}
	}

	/**
	 * Replicates each entry in allTokensSentences so that there are copies times more instances of the entry
	 * with copies of a given entry being placed together, preserving original ordering or unique entries 
	 * @param allTokensSentences
	 * @param copies
	 */
	private static void replicateTokenSentences(List<List<SyllableToken>> allTokensSentences, int copies) {
		if (copies == 1) return;
		else {
			int allTokensSize = allTokensSentences.size();
			for (int i = allTokensSize-1; i >=0; i--) {
				List<SyllableToken> sentenceToReplicate = allTokensSentences.get(i);
				allTokensSentences.add(i,new ArrayList<SyllableToken>(sentenceToReplicate));
			}
		}
	}

}
