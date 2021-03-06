package dbtb.data;

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

import dbtb.linguistic.phonetic.Phoneme;
import dbtb.linguistic.phonetic.Phoneticizer;
import dbtb.linguistic.phonetic.VowelPhoneme;
import dbtb.linguistic.phonetic.syllabic.WordSyllables;
import dbtb.linguistic.syntactic.Pos;
import dbtb.linguistic.syntactic.StanfordNlpInterface;
import dbtb.main.Main;
import dbtb.markov.BidirectionalVariableOrderPrefixIDMap;
import dbtb.markov.Token;
import dbtb.utils.Pair;
import dbtb.utils.Utils;
import edu.stanford.nlp.util.CoreMap;

public class DataLoader {
	
//	private static final long MAX_TRAINING_SENTENCES = -1; // -1 for no limit - CAN'T USE THIS TO THROTTLE ANY MORE - requires syncing
	private static final long MAX_TOKENS_PER_SENTENCE = 30; // keeps Stanford NLP fast
	private static final int NUM_THREADS = Runtime.getRuntime().availableProcessors()-1;
	private static final int DEBUG = 1; 
	private static final double MAX_MEMORY_FOR_BASE_MODEL = 50.;
	final private int BATCH_SIZE = 200;
	
	private static final boolean USE_DUMMY_DATA = false;
	private static final String datadir = "/Users/bodipaul/Archive/2017_BYU/ComputationalCreativity/data/COCA Text DB/";
	private static final Map<String, String> filePrefixMap = new HashMap<String,String>();
	static {
		filePrefixMap.put("spoken", "text_spoken_kde/w_spok_");
		filePrefixMap.put("fiction", "text_fiction_awq/w_fic_");
		filePrefixMap.put("magazine", "text_magazine_qrb/w_mag_");
	};
	
	public static String trainingSource = "fiction";
	
	private String[] TRAINING = new String[]{
			"iced cakes inside The Bake",
			"Have you seen a moose with a pair of new shoes?",
			"Have you ever seen a bear combing his hair?",
			"Have you ever seen a llama wearing polka dot pajamas?",
			"Have you ever seen a llama wearing pajamas?",
			"Have you ever seen a moose with a pair of new shoes?",
			"Have you ever seen a pirate that just ate a veggie diet?",
			"Have you ever seen a law drinking from a straw?"
//			"I'm a bear combin' his hair?",
//			"Why is it so weird to think about a llama wearing polka dot pajamas?",
//			"I have a llama wearing pajamas.",
//			"Have you a pirate that just ate a veggie diet?",
	};
	
	
	public class DataProcessor {

		private static final int LAST_FILE_NUMBER = 22;
		private String[][] trainingSentences;
		private int[] nextBatchIdxes;
		
		public DataProcessor() {
			this.trainingSentences = new String[USE_DUMMY_DATA?1:(LAST_FILE_NUMBER + 1)][];
			this.nextBatchIdxes = new int[USE_DUMMY_DATA?1:(LAST_FILE_NUMBER + 1)];
			loadNextFile(0);
		}
		
		private synchronized int getNextBatchStart(int source) {
			int nextBatchStart = nextBatchIdxes[source];
			nextBatchIdxes[source] += BATCH_SIZE;
			
			if (nextBatchStart > trainingSentences[source].length && source < trainingSentences.length-1 && trainingSentences[source+1] == null) {
				loadNextFile(source+1);
			}
			
			return nextBatchStart;
		}
		
		private void loadNextFile(int source) {

			String[] trainingSentencesFromFile;
			if (USE_DUMMY_DATA) {
				trainingSentencesFromFile = TRAINING ;
			}
		
			if (!USE_DUMMY_DATA) {
				StringBuilder str = new StringBuilder();
				try {
					final String fileName = datadir + filePrefixMap.get(trainingSource) + (source+1990) + ".txt";
					if (DEBUG > 0) System.out.println("Loading " + fileName + " for training");
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
				trainingSentencesFromFile = fileContents.split(" [[^a-zA-Z']+ ]+");
			}
			
			trainingSentences[source] = trainingSentencesFromFile;
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
						
						Map<Integer, Double> priorCountsForBatch = new HashMap<Integer, Double>();
						Map<Integer, Map<Integer, Double>> transitionCountsForBatch = new HashMap<Integer, Map<Integer, Double>>();
						BidirectionalVariableOrderPrefixIDMap<SyllableToken> prefixIDMapForBatch = new BidirectionalVariableOrderPrefixIDMap<SyllableToken>(order);

						int sentencePronunciationsTrainedOnForBatch = 0;
						int sentencesTrainedOnForBatch = 0;
						
						int currentSource = 0;
						
						int nextBatchStart = getNextBatchStart(currentSource);
						if (nextBatchStart > trainingSentences[currentSource].length) {
							currentSource++;
							if (currentSource < trainingSentences.length)
								nextBatchStart = getNextBatchStart(currentSource);
						}

						int nextBatchEnd;
						boolean threadReadyToSync = false;
						
						while((!threadReadyToSync || threadCurrentlySyncing()) && currentSource < trainingSentences.length && nextBatchStart < trainingSentences[currentSource].length) {
							nextBatchEnd = Math.min(nextBatchStart+BATCH_SIZE, trainingSentences[currentSource].length);
							System.out.println(Thread.currentThread().getName() + " training on sentences " + nextBatchStart + " to " + (nextBatchEnd-1) + " from " + (currentSource + 1990) + " (MEM:" + Main.computePercentTotalMemoryUsed() + "%)");
							for (int i = nextBatchStart; i < nextBatchEnd; i++) {
								String trainingSentence = trainingSentences[currentSource][i];
								//start thread
								// get syllable tokens for all unique pronunciations of the sentence
								List<List<SyllableToken>> trainingTokensSentences = convertToSyllableTokens(cleanSentence(trainingSentence));
								// if there was no valid pronunciation, skip it
								if (trainingTokensSentences == null) continue;
								// for each pronunciation
								if (DEBUG > 1) System.out.println("PRON COUNT:" + trainingTokensSentences.size());
								final double trainingWeight = 1.0/trainingTokensSentences.size();
								// Start synchronization
								Integer fromTokenID, toTokenID;
								int sentencePronunciationsTrainedOnForSentence = 0;
								for (List<SyllableToken> trainingSentenceTokens : trainingTokensSentences) {
									if (trainingSentenceTokens.size() < order) continue;
	//								LinkedList<Token> prefix = new LinkedList<Token>(Collections.nCopies(order, Token.getStartToken()));
									LinkedList<SyllableToken> prefix = new LinkedList<SyllableToken>(trainingSentenceTokens.subList(0, order));
									//TODO add string associated w/ prefix to set for Word2Vec
									fromTokenID = prefixIDMap.addPrefix(prefix);
									for (int j = order; j < trainingSentenceTokens.size(); j++ ) {
										prefix.removeFirst();
										prefix.addLast(trainingSentenceTokens.get(j));
										
										toTokenID = prefixIDMap.addPrefix(prefix);
										Utils.incrementValueForKeys(transitionCountsForBatch, fromTokenID, toTokenID, trainingWeight);
										Utils.incrementValueForKey(priorCountsForBatch, fromTokenID, trainingWeight); // we do this for every token 
	
										fromTokenID = toTokenID;
									}
									sentencePronunciationsTrainedOnForSentence++;
								}
								if (DEBUG > 1) System.out.println("sentencesTrainedOn:" + sentencesTrainedOn + ", sentencePronunciationsTrainedOn:" + sentencePronunciationsTrainedOn + " transitions.size()=" + transitions.size() + " prefixIDMap.getPrefixCount()=" + prefixIDMapForBatch.getPrefixCount());
								
								if (sentencePronunciationsTrainedOnForSentence > 0){
									sentencesTrainedOnForBatch++;
									sentencePronunciationsTrainedOnForBatch += sentencePronunciationsTrainedOnForSentence;
								}
							}
							
							if (returnStatus != 1 && Main.computePercentTotalMemoryUsed() > MAX_MEMORY_FOR_BASE_MODEL) {
								System.out.println("Hit memory threshold of " + MAX_MEMORY_FOR_BASE_MODEL + " for base model training");
								returnStatus = 1;
							}
							if (returnStatus == 1) {
								threadReadyToSync = true;
							}
							
							nextBatchStart = getNextBatchStart(currentSource);
							if (nextBatchStart > trainingSentences[currentSource].length) {
								currentSource++;
								if (currentSource < trainingSentences.length)
									nextBatchStart = getNextBatchStart(currentSource);
							}
						}
						System.out.println(Thread.currentThread().getName() + " finished training");
						incrementSentencesAndPronunciationsTrainedOn(sentencesTrainedOnForBatch, sentencePronunciationsTrainedOnForBatch);
						incrementTransitionsAndPriors(transitionCountsForBatch, priorCountsForBatch);
					}
				});
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
				LinkedList<SyllableToken> fromState, toState;
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

		/**
		 * Assumes global ids
		 * @param transitionCountsForBatch
		 * @param priorCountsForBatch
		 */
		private synchronized void incrementTransitionsAndPriors(Map<Integer, Map<Integer, Double>> transitionCountsForBatch,
				Map<Integer, Double> priorCountsForBatch) {
			
			System.out.println("Synchronizing batch transitions and priors for thread " + Thread.currentThread().getName() + "... ");
			
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
			
			threadCurrentlySyncing = false;
			System.out.println("Synchronization Complete!");
		}
	}
	

	public static StanfordNlpInterface nlp = new StanfordNlpInterface();

	public static class DataSummary<T extends Token> {
		/**
		 * This map serves to store all possible tokens seen in the data set. Representing
		 * each prefix as an integer saves time and space later on. Essentially each new token (after
		 * checking it is not already in the map) is added thus: int tokenID = statesByIndex.addPrefix(prefix), 
		 * where prefix is an object of type LinkedList<SyllableToken>.
		 * The integer value in the map is what is used as the key for both the priors and the transitions data
		 * structures.
		 */
		public BidirectionalVariableOrderPrefixIDMap<T> statesByIndex;
		
		/**
		 * The inner key k_1 and outer key k_2 are the prefix IDs (see description of statesByIndex) for the from- and to-tokens respectively.
		 * The value is the transition probability of going from k_1 to k_2 as learned empirically from the data.
		 * Only insert inner and outer keys for non-zero transition probabilities (i.e., the absence
		 * of a key is used to indicate a probability of 0). k_1 and k_2 are both sequences of tokens of length markovOrder.
		 */
		public Map<Integer, Map<Integer, Double>> transitions;

		public Map<Integer, Double> priors;

		public DataSummary(BidirectionalVariableOrderPrefixIDMap<T> statesByIndex, Map<Integer, Double> priors, Map<Integer, Map<Integer, Double>> transitions) {
			this.statesByIndex = statesByIndex;
			this.transitions = transitions;
			this.priors = priors;
		}
	}

	private int order;
	private BidirectionalVariableOrderPrefixIDMap<SyllableToken> prefixIDMap;
	private Map<Integer, Double> priors = new HashMap<>();
	private Map<Integer, Map<Integer, Double>> transitions = new HashMap<>();
	private long sentencesTrainedOn = 0;
	private long sentencePronunciationsTrainedOn = 0;
	
	public DataLoader(int markovOrder) {
		this.order = markovOrder;
		this.prefixIDMap = new BidirectionalVariableOrderPrefixIDMap<>(order);
	}

	public DataSummary<SyllableToken> loadData() throws InterruptedException {
		
		DataProcessor dp = new DataProcessor();
		int status = dp.process();
		
		System.out.println("Trained on " + sentencesTrainedOn + " sentences and " + sentencePronunciationsTrainedOn + " pronunciations");

		Utils.normalize(priors);
		Utils.normalizeByFirstDimension(transitions);
		
		DataSummary<SyllableToken> summary = new DataSummary<SyllableToken>(prefixIDMap, priors, transitions);
		return summary;
	}

	private synchronized void incrementSentencesAndPronunciationsTrainedOn(int sentencesTrainedOn, int sentencePronunciationsTrainedOn) {
		this.sentencesTrainedOn += sentencesTrainedOn;
		this.sentencePronunciationsTrainedOn += sentencePronunciationsTrainedOn;
	}

	final private static String[] suffixes = new String[]{" n't "," ' "," 's "," 've ", " 'd ", " 'll ", " 're ", " 't "," 'm "};
	public String cleanSentence(String trainingSentence) {
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

	public static List<List<SyllableToken>> convertToSyllableTokens(String trainingSentence) {
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
	
	public static void main(String[] args) {
		DataLoader dl = new DataLoader(4);
		convertToSyllableTokens(dl.cleanSentence("This is a sign of the decline of"));
	}

}
