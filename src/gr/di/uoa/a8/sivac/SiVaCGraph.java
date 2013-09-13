package gr.di.uoa.a8.sivac;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashSet;

import com.google.common.io.Files;

import gr.di.uoa.a8.sivac.utils.CalculateFrequencies;
import gr.di.uoa.a8.sivac.utils.SiVaCUtils;
import it.unimi.dsi.webgraph.ArcListASCIIGraph;
import it.unimi.dsi.webgraph.BVGraph;
import it.unimi.dsi.webgraph.ImmutableGraph;
import it.unimi.dsi.webgraph.LazyIntIterator;

public class SiVaCGraph extends ImmutableGraph {

	private static final String SiVaC_EXTENSION = "a8";
	private ImmutableGraph ig;
	private int D;
	private int size = 0; /* number of nodes in the graph */
	private int edges;
	private byte[] diagonal /* the diagonal as bytes */;
	private File tempD /*
						 * file descriptor for a temp file with the arc list for
						 * the diagonal part
						 */;
	private File tempNoD /*
						 * file descriptor for a temp file with the arc list for
						 * the non diagonal part
						 */;
	private HashSet<Integer> nodes;

	public SiVaCGraph(InputStream is, int d) throws IOException {
		this.D = d;
		this.nodes = new HashSet<Integer>();
		createTempFiles(is);
		this.ig = ArcListASCIIGraph.loadOnce(new FileInputStream(tempNoD));
		this.store("temp");
		CalculateFrequencies.calculateFrequencies(this.diagonal, this.size, this.D);
		//this.diagonal = loadDiagonal(new File("temp." + SiVaC_EXTENSION));
		this.ig = BVGraph.load("temp");
	}

	/**
	 * Function that reads the arc list file and splits into two temp files, one
	 * for the diagonal and one for the non diagonal part and creates a list of
	 * nodes
	 * */
	private boolean createTempFiles(InputStream is) throws IOException {

		BufferedReader br = new BufferedReader(new InputStreamReader(is));
		tempD = File.createTempFile("SiVaC-D-", ".a8");
		tempNoD = File.createTempFile("SiVaC-NoD-", ".a8");
		tempD.deleteOnExit();
		tempNoD.deleteOnExit();
		BufferedWriter bwD = new BufferedWriter(new FileWriter(tempD.getAbsoluteFile()));
		BufferedWriter bwNoD = new BufferedWriter(new FileWriter(tempNoD.getAbsoluteFile()));
		String line;
		while ((line = br.readLine()) != null) {
			String[] temp = line.split("\\s+");
			int a = Integer.parseInt(temp[0]);
			int b = Integer.parseInt(temp[1]);
			this.nodes.add(a);
			this.nodes.add(b);
			this.edges++;
			// size of the graph (nodes size) is equal to the largest + 1
			if (a >= this.size)
				this.size = a + 1;
			if (b >= this.size)
				this.size = b + 1;
			// write to one of the temp files
			if (SiVaCUtils.isDiagonal(a, b, D)) {
				// in the diagonal
				bwD.write(line + '\n');
			} else {
				// outside the diagonal
				bwNoD.write(line + '\n');
			}
		}
		br.close();
		bwD.close();
		bwNoD.close();
		return true;
	}

	/** get position in file from node pair */
	public static int getSerialization(int a, int b, int size, int D) {
		// check if input is valid
		if ((a > b + D || a < b - D) || (a < 0 || b < 0) || a >= size || b >= size)
			throw new IllegalArgumentException("not a valid node pair: (" + a + ", " + b + ")");
		// calculate position
		// TODO what about social?
		int no = a * (2 * D + 1) + b + D - a;
		int temp = D;
		// remove missing from beginning
		for (int i = 0; i < D; i++) {
			if (a >= i) {
				no -= temp;
				temp--;
			}
		}
		// TODO correct?
		temp = 1;
		// remove missing from end
		for (int i = size + 1 - D; i < size; i++) {
			if (a >= i) {
				no -= temp;
				temp++;
			}
		}
		return no;
	}

	// tests if bit is set in a byte
	public static boolean isSet(byte my_byte, int pos) {
		if (pos > 7 || pos < 0)
			throw new IllegalArgumentException("not a valid bit position: " + pos);
		return (my_byte & (1 << pos)) != 0;
	}

	// set a bit in a byte
	public static byte set_bit(byte my_byte, int pos) {
		if (pos > 7 || pos < 0)
			throw new IllegalArgumentException("not a valid bit position: " + pos);
		return (byte) (my_byte | (1 << pos));
	}

	// unset a bit in a byte
	public static byte unset_bit(byte my_byte, int pos) {
		if (pos > 7 || pos < 0)
			throw new IllegalArgumentException("not a valid bit position: " + pos);
		return (byte) (my_byte & ~(1 << pos));
	}

	// public static SiVaCGraph loadOnce(InputStream is) {
	// return loadOnce(is, 1);
	// }

	public static SiVaCGraph createAndLoad(InputStream is, int d, String basename) {
		SiVaCGraph sg;
		try {
			sg = new SiVaCGraph(is, d);
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
		return sg;
	}

	@Override
	public ImmutableGraph copy() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int numNodes() {
		return this.size;
	}

	public int numEdges() {
		return this.edges;
	}

	@Override
	public int outdegree(int arg0) {
		return ig.outdegree(arg0);
	}

	@Override
	public boolean randomAccess() {
		// TODO Auto-generated method stub
		return false;
	}

	public boolean store(String basename) {
		// store diagonal part
		// TODO check!
		int largest = getSerialization(this.size - 1, this.size - 1, size, D);
		this.diagonal = new byte[largest / 8 + (largest % 8 != 0 ? 1 : 0)];
		String line;
		try {
			BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(tempD)));
			while ((line = br.readLine()) != null) {
				String[] temp = line.split("\\s+");
				int a = Integer.parseInt(temp[0]);
				int b = Integer.parseInt(temp[1]);
				int no = getSerialization(a, b, size, D);
				this.diagonal[no / 8] = set_bit(this.diagonal[no / 8], no % 8);
			}
			br.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		FileOutputStream fos;
		try {
			fos = new FileOutputStream(basename + "." + SiVaC_EXTENSION);
			fos.write(this.diagonal);
			fos.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		// store non diagonal part as BVGraph
		try {
			ImmutableGraph.store(BVGraph.class, this.ig, basename);
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}

	public byte[] loadDiagonal(File file) throws IOException {
		return Files.toByteArray(file);
	}

	public boolean isSuccessor(int a, int b) {
		if (SiVaCUtils.isDiagonal(a, b, D)) {
			int no = getSerialization(a, b, size, D);
			return (isSet(this.diagonal[no / 8], no % 8));
		} else {
			int[] temp = this.ig.successorArray(a);
			for (int suc : temp) {
				if (suc == b)
					return true;
			}
		}
		return false;
	}

	public LazyIntIterator getSuccessors(int a) {
		return this.ig.successors(a);
	}

	private void checkAllEdges(FileInputStream fileInputStream) throws NumberFormatException, IOException {
		BufferedReader br = new BufferedReader(new InputStreamReader(fileInputStream));
		String line;
		while ((line = br.readLine()) != null) {
			String[] temp = line.split("\\s+");
			int a = Integer.parseInt(temp[0]);
			int b = Integer.parseInt(temp[1]);
			if (!this.isSuccessor(a, b))
				throw new RuntimeException("Edge not found " + a + " " + b);
		}
		br.close();
	}

	public static void main(String[] args) throws NumberFormatException, IOException {
		SiVaCGraph a = SiVaCGraph.createAndLoad(new FileInputStream(new File("/var/www/graphs/cnr-2000/cnr-2000.txt")), 3, "test");
		a.checkAllEdges(new FileInputStream(new File("/var/www/graphs/cnr-2000/cnr-2000.txt")));
		// a.getSuccessors(5);
		// a.store("test");
	}

}
