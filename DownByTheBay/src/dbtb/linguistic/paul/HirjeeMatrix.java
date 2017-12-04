package dbtb.linguistic.paul;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import dbtb.linguistic.phonetic.ConsonantPhoneme;
import dbtb.linguistic.phonetic.VowelPhoneme;
import dbtb.linguistic.phonetic.syllabic.Syllable;
import dbtb.utils.Pair;

public class HirjeeMatrix {

	private static double[][] matrix = load();
	private static final String hirjeeFilePath = "data/hirjeeMatrix_withY.txt";
	private static final int UNMATCHED_CODA_CONSONANT_AT_BEGINNING = 39;
	private static final int UNMATCHED_CODA_CONSONANT_AT_END = 40;
	public final static double HIRJEE_RHYME_THRESHOLD = 3.0;

	public static double[][] load() {
		if (matrix == null) {

			BufferedReader bf;
			try {
				bf = new BufferedReader(new FileReader(hirjeeFilePath));

				String line = bf.readLine();
				String[] lineSplit = line.split("\t");
				int matrixWidth = lineSplit.length - 1;
				double value;
				matrix = new double[matrixWidth][matrixWidth];
				// System.out.println(Arrays.toString(lineSplit));
				// System.out.println(matrix.length);
				// System.out.println(matrix[1].length);

				int lineNum = 0;
				while ((line = bf.readLine()) != null) {
					lineSplit = line.split("\t");
					for (int i = lineNum; i < matrixWidth; i++) {
						if (i + 1 >= lineSplit.length || lineSplit[i + 1].length() == 0) {
							value = Double.NEGATIVE_INFINITY;
						} else {
							value = Double.parseDouble(lineSplit[i + 1]);
						}

						matrix[lineNum][i] = value;
						matrix[i][lineNum] = value;
					}
					lineNum++;
				}

				bf.close();
			} catch (NumberFormatException | IOException e) {
				e.printStackTrace();
				throw new RuntimeException();
			}
		}

		return matrix;
	}

	public static void main(String[] args) throws IOException {
		double[][] matrix = HirjeeMatrix.load();
		Map<String, Pair<Integer, PhoneCategory>> phonesDict = Phonetecizer.loadPhonesDict();

		for (double[] array : matrix) {
			for (double num : array)
				System.out.print(num + "\t");
			System.out.println();
		}

		for (String phone : phonesDict.keySet()) {
			System.out.println("AA with " + phone + ": "
					+ matrix[phonesDict.get(phone).getFirst()][phonesDict.get("AA").getFirst()]);
		}
	}
	
	public static double scoreSyllables(Syllable s1, Syllable s2) {
		
		// vowel
		double vowelScore = 0.0;
		VowelPhoneme vowel1 = s1.getVowel();
		VowelPhoneme vowel2 = s2.getVowel();
		vowelScore += matrix[vowel1.phonemeEnum.ordinal()][vowel2.phonemeEnum.ordinal()];
//		System.out.println("VOWEL:" + vowel1.phonemeEnum + " " + vowel1.phonemeEnum.ordinal() + ", " + vowel2.phonemeEnum + " " + vowel2.phonemeEnum.ordinal());
		// coda
		double codaScore = 0.0;
		List<ConsonantPhoneme> coda1 = s1.getCoda();
		List<ConsonantPhoneme> coda2 = s2.getCoda();

		if (coda1.isEmpty() || coda2.isEmpty()) {
			if (coda1.isEmpty() && coda2.isEmpty()) {
				codaScore = 3.0;
			} else {
				for (ConsonantPhoneme consonantPhoneme : coda1) {
					final int consIdx = consonantPhoneme.phonemeEnum.ordinal();
					codaScore = Math.max(matrix[consIdx][UNMATCHED_CODA_CONSONANT_AT_BEGINNING], matrix[consIdx][UNMATCHED_CODA_CONSONANT_AT_END]);
				}
				for (ConsonantPhoneme consonantPhoneme : coda2) {
					final int consIdx = consonantPhoneme.phonemeEnum.ordinal();
					codaScore = Math.max(matrix[consIdx][UNMATCHED_CODA_CONSONANT_AT_BEGINNING], matrix[consIdx][UNMATCHED_CODA_CONSONANT_AT_END]);
				}
				codaScore /= (coda1.size() + coda2.size());
			}
		} else {
			double[][] alignmentMatrix = new double[coda1.size()+1][coda2.size()+1];
			alignmentMatrix[0][0] = 0;
			char[][] backtrack = new char[coda1.size()+1][coda2.size()+1];

			int cons1Idx, cons2Idx;
			for (int row = 1; row <= coda1.size(); row++) {
				cons1Idx = coda1.get(row-1).phonemeEnum.ordinal();
				alignmentMatrix[row][0] = alignmentMatrix[row-1][0] + matrix[cons1Idx][UNMATCHED_CODA_CONSONANT_AT_BEGINNING];
				backtrack[row][0] = 'U';
			}
			for (int col = 1; col <= coda2.size(); col++) {
				cons2Idx = coda2.get(col-1).phonemeEnum.ordinal();
				alignmentMatrix[0][col] = matrix[UNMATCHED_CODA_CONSONANT_AT_BEGINNING][cons2Idx];
				backtrack[0][col] = 'L';
			}
			
			double diag, up, left;
			for (int row = 1; row <= coda1.size(); row++) {
				double[] prevMatrixRow = alignmentMatrix[row-1];
				double[] currMatrixRow = alignmentMatrix[row];
				char[] currBackTrackRow = backtrack[row];
				cons1Idx = coda1.get(row-1).phonemeEnum.ordinal();
				for (int col = 1; col <= coda2.size(); col++) {
					cons2Idx = coda2.get(col-1).phonemeEnum.ordinal();
//					System.out.println("CONSONANT:" + coda1.get(row-1).phonemeEnum + " " + coda1.get(row-1).phonemeEnum.ordinal() + ", " + coda2.get(col-1).phonemeEnum + " " + coda2.get(col-1).phonemeEnum.ordinal());

					diag = prevMatrixRow[col-1] + matrix[cons1Idx][cons2Idx];
					left = currMatrixRow[col-1] + (row == coda1.size()? matrix[UNMATCHED_CODA_CONSONANT_AT_END][cons2Idx] : Double.NEGATIVE_INFINITY);
					up = prevMatrixRow[col] + (col == coda2.size()? matrix[cons1Idx][UNMATCHED_CODA_CONSONANT_AT_END] : Double.NEGATIVE_INFINITY);
	
					if (diag >= up) {
						if (diag >= left) {
							currMatrixRow[col] = diag;
							currBackTrackRow[col] = 'D';
						} else {
							currMatrixRow[col] = left;
							currBackTrackRow[col] = 'L';
						}
					} else {
						if (up >= left) {
							currMatrixRow[col] = up;
							currBackTrackRow[col] = 'U';
						} else {
							currMatrixRow[col] = left;
							currBackTrackRow[col] = 'L';
						}
					}
				}
			}
			
			int pathLen = 0;
			int row = coda1.size();
			int col = coda2.size();
			while(row != 0 || col != 0) {
				pathLen++;
				switch(backtrack[row][col]) {
				case 'D':
					row--;
					col--;
					break;
				case 'L':
					col--;
					break;
				case 'U':
					row--;
					break;
				}
			}
			
			codaScore = alignmentMatrix[coda1.size()][coda2.size()]/pathLen;
//			for (double[] ds : alignmentMatrix) {
//				System.out.println(Arrays.toString(ds));
//			}
		}
		
		// stress
		double stressScore = 0.0;
		
		final double score = vowelScore + codaScore + stressScore;
//		if (score > HIRJEE_RHYME_THRESHOLD)
//			System.out.print(s1 + " + " + s2 + " + " + vowelScore + " + " + codaScore + " + " + stressScore + " = " + score);
		return score;
	}
	
	public static double score(int phone, int phone2) {
		return matrix[phone][phone2];
	}

}
