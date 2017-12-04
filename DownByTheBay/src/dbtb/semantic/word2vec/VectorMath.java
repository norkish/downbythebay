package dbtb.semantic.word2vec;

public abstract class VectorMath {

	public static void main(String[] args) {
		double[] v1 = {
				10,
				200,
				3000000
		};
		double[] v2 = {
				-1,
				-2,
				-3
		};
		System.out.println("Similarity: " + cosineSimilarity(v1,v2));
//		System.out.println("Distance: " + cosineDistance(v1,v2));
	}

//	public static double cosineDistance(double[] v1, double[] v2) {
//		return 1.0 + cosineSimilarity(v1,v2);
//	}

	public static double cosineSimilarity(double[] v1, double[] v2) {
		return Math.abs( dotProduct(v1,v2) / (euclideanNorm(v1) * euclideanNorm(v2)) );//TODO is it correct to take the absolute value?
	}

	public static double dotProduct(double[] v1, double[] v2) {
		double total = 0;
		for (int i = 0; i < v1.length; i++) {
			total += (v1[i] * v2[i]);
		}
		return total;
	}

	public static double euclideanNorm(double[] v) {
		return Math.sqrt(dotProduct(v,v));
	}

}
