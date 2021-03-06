package opentree;

/*
 * GraphExporter is meant to export the graph in a variety of 
 * formats
 * GraphExporter major functions
 * =============================
 * mrpdump
 * write graph ml
 * write json
 */

import jade.tree.JadeNode;
import jade.tree.JadeTree;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
//import java.util.Iterator;
import java.util.Stack;

//import opentree.RelTypes;

import org.neo4j.graphalgo.GraphAlgoFactory;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphalgo.PathFinder;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.traversal.Evaluators;
import org.neo4j.graphdb.traversal.TraversalDescription;
import org.neo4j.kernel.EmbeddedGraphDatabase;
import org.neo4j.kernel.Traversal;

public class GraphExporter extends GraphBase {

	private SpeciesEvaluator se;
	private ChildNumberEvaluator cne;
	private TaxaListEvaluator tle;

	public GraphExporter() {
		finishInitialization();
	}

	public GraphExporter(String graphname) {
		graphDb = new GraphDatabaseAgent(graphname);
		finishInitialization();
	}

	public GraphExporter(EmbeddedGraphDatabase graphn) {
		graphDb = new GraphDatabaseAgent(graphn);
		finishInitialization();
	}

	public GraphExporter(GraphDatabaseService gdb) {
		graphDb = new GraphDatabaseAgent(gdb);
		finishInitialization();
	}

	private void finishInitialization() {
		cne = new ChildNumberEvaluator();
		cne.setChildThreshold(100);
		se = new SpeciesEvaluator();
		tle = new TaxaListEvaluator();
		if (graphDb != null) {
			graphNodeIndex = graphDb.getNodeIndex("graphNamedNodes");
			sourceRelIndex = graphDb.getRelIndex("sourceRels");
			sourceRootIndex = graphDb.getNodeIndex("sourceRootNodes");
		}
	}

	public void writeGraphML(String taxname, String outfile, boolean useTaxonomy) 
				throws TaxonNotFoundException {
		Node firstNode = findTaxNodeByName(taxname);
		String tofile = getGraphML(firstNode,useTaxonomy);
		PrintWriter outFile;
		try {
			outFile = new PrintWriter(new FileWriter(outfile));
			outFile.write(tofile);
			outFile.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * creates a graphml for viewing in gephi Because gephi cannot view parallel lines, this will not output parallel edges. The properties that we have are
	 * node taxon edge sourcename (this is more complicated because of not outputting parallel edges) node support edge support node effective parents node
	 * average effective parents node delta average effective parents
	 * 
	 * measurement of effective parents is measured as Inverse Simpson index:$N=\frac{1}{\sum_{i=1}^{n}p_{i}^{2}}$
	 * 
	 * @param startnode
	 * @return
	 */

	private String getGraphML(Node startnode,boolean taxonomy){
		StringBuffer retstring = new StringBuffer("<graphml>\n");
		retstring.append("<key id=\"d0\" for=\"node\" attr.name=\"taxon\" attr.type=\"string\">\n");
		retstring.append("<default></default>\n");
		retstring.append("</key>\n");
		retstring.append("<key id=\"d1\" for=\"edge\" attr.name=\"sourcename\" attr.type=\"string\">\n");
		retstring.append("<default></default>\n");
		retstring.append("</key>\n");
		retstring.append("<key id=\"d2\" for=\"node\" attr.name=\"support\" attr.type=\"double\">\n");
		retstring.append("<default></default>\n");
		retstring.append("</key>\n");
		retstring.append("<key id=\"d3\" for=\"edge\" attr.name=\"support\" attr.type=\"double\">\n");
		retstring.append("<default></default>\n");
		retstring.append("</key>\n");
		retstring.append("<key id=\"d4\" for=\"node\" attr.name=\"effpar\" attr.type=\"double\">\n");
		retstring.append("<default></default>\n");
		retstring.append("</key>\n");
		retstring.append("<key id=\"d5\" for=\"node\" attr.name=\"effch\" attr.type=\"double\">\n");
		retstring.append("<default></default>\n");
		retstring.append("</key>\n");
		retstring.append("<key id=\"d6\" for=\"node\" attr.name=\"avgeffparsubtree\" attr.type=\"double\">\n");
		retstring.append("<default></default>\n");
		retstring.append("</key>\n");
		retstring.append("<key id=\"d7\" for=\"node\" attr.name=\"avgdeltaparsubtree\" attr.type=\"double\">\n");
		retstring.append("<default></default>\n");
		retstring.append("</key>\n");
		retstring.append("<graph id=\"G\" edgedefault=\"directed\">\n");
		// get the list of nodes
		HashSet<Node> nodes = new HashSet<Node>();
		for (Node tnode : Traversal.description().relationships(RelTypes.STREECHILDOF, Direction.INCOMING)
				.traverse(startnode).nodes()) {
			nodes.add(tnode);
		}
		HashMap<Long, Double> nodesupport = new HashMap<Long, Double>();
		HashMap<Long, HashMap<Long, Double>> edgesupport = new HashMap<Long, HashMap<Long, Double>>();
		HashMap<Long, ArrayList<String>> sourcelists = new HashMap<Long, ArrayList<String>>();// number of sources for each node
		HashMap<Long, Double> effpar = new HashMap<Long, Double>();
		HashMap<Long, ArrayList<Double>> avgeffparnums = new HashMap<Long, ArrayList<Double>>();
		HashMap<Long, Double> effch = new HashMap<Long, Double>();
		HashMap<Long, HashMap<Long, Integer>> parcounts = new HashMap<Long, HashMap<Long, Integer>>();
		HashMap<Long, Double> avgdelta = new HashMap<Long, Double>();
		// do most of the calculations
		/*
		 * calculations here are effective parents and effective children
		 */
		for (Node tnode : nodes) {
			HashMap<Long, Integer> parcount = new HashMap<Long, Integer>();
			ArrayList<String> slist = new ArrayList<String>();
			int relcount = 0;
			for (Relationship rel : tnode.getRelationships(Direction.OUTGOING, RelTypes.STREECHILDOF)) {
				if (taxonomy == true || ((String) rel.getProperty("source")).compareTo("taxonomy") != 0) {
					if (parcount.containsKey(rel.getEndNode().getId()) == false) {
						parcount.put(rel.getEndNode().getId(), 0);
					}
					Integer tint = (Integer) parcount.get(rel.getEndNode().getId()) + 1;
					parcount.put(rel.getEndNode().getId(), tint);
					relcount += 1;
					slist.add((String) rel.getProperty("source"));
				}
			}
			sourcelists.put(tnode.getId(), slist);
			// calculate the inverse Simpson effective number of parents
			double efp = 0.0;
			for (Long tl : parcount.keySet()) {
				efp += (parcount.get(tl) / (double) relcount) * (parcount.get(tl) / (double) relcount);
			}
			efp = 1 / efp;
			effpar.put(tnode.getId(), efp);
			parcounts.put(tnode.getId(), parcount);

			// calculate the inverse Simpson effective number of children
			HashMap<Long, Integer> chcount = new HashMap<Long, Integer>();
			relcount = 0;
			for (Relationship rel : tnode.getRelationships(Direction.INCOMING, RelTypes.STREECHILDOF)) {
				if (taxonomy == true || ((String) rel.getProperty("source")).compareTo("taxonomy") != 0) {
					if (chcount.containsKey(rel.getStartNode().getId()) == false) {
						chcount.put(rel.getStartNode().getId(), 0);
					}
					Integer tint = (Integer) chcount.get(rel.getStartNode().getId()) + 1;
					chcount.put(rel.getStartNode().getId(), tint);
					relcount += 1;
				}
			}
			double efc = 0.0;
			if (relcount > 0) {
				for (Long tl : chcount.keySet()) {
					efc += (chcount.get(tl) / (double) relcount) * (chcount.get(tl) / (double) relcount);
				}
				efc = 1 / efc;
			}
			effch.put(tnode.getId(), efc);
		}
		// calculate node support
		/*
		 * node support here is calculated as the number of outgoing edges divided by the total number of sources in the subtree obtained by getting the number
		 * of unique sources at each tip
		 */
		for (Node tnode : nodes) {
			
//			System.out.println(tnode.getProperty("name"));
			
			HashSet<String> sources = new HashSet<String>();
			long[] mrcas = (long[]) tnode.getProperty("mrca");
			for (int i = 0; i < mrcas.length; i++) {
				sources.addAll(sourcelists.get((Long) mrcas[i]));
			}
			double supp = (double) sourcelists.get(tnode.getId()).size() / (double) sources.size();
			// give tips no support so they don't give weird looking
			if (tnode.hasRelationship(Direction.INCOMING, RelTypes.STREECHILDOF) == false) {
				nodesupport.put(tnode.getId(), 1.);
			} else {
				nodesupport.put(tnode.getId(), supp);
			}
			HashMap<Long, Integer> parcount = parcounts.get(tnode.getId());
			HashMap<Long, Double> edgesupp = new HashMap<Long, Double>();
			for (Long tl : parcount.keySet()) {
				double dedgesupp = (double) parcount.get(tl) / (double) sources.size();
				edgesupp.put(tl, dedgesupp);
			}
			edgesupport.put(tnode.getId(), edgesupp);
			// add effpar to parents
			Stack<Long> ndl = new Stack<Long>();
			for (Long tndl : parcounts.get(tnode.getId()).keySet()) {
				ndl.add(tndl);
			}
			HashSet<Long> done = new HashSet<Long>();
			while (ndl.isEmpty() == false) {
				Long curnodeid = ndl.pop();
				done.add(curnodeid);

//				System.out.println(parcounts.get(curnodeid));
				
				for (Long tndl : parcounts.get(curnodeid).keySet()) {
					if (done.contains(tndl) == false) {
						ndl.push(tndl);
					}
				}
				ArrayList<Double> tarl;
				if (avgeffparnums.containsKey(curnodeid) == false) {
					tarl = new ArrayList<Double>();
					tarl.add(0.0);
					tarl.add(0.0);
					avgeffparnums.put(curnodeid, tarl);
				}
				tarl = avgeffparnums.get(curnodeid);
				avgeffparnums.get(curnodeid).set(0, tarl.get(0) + (effpar.get(tnode.getId()) * nodesupport.get(tnode.getId())));
				avgeffparnums.get(curnodeid).set(1, tarl.get(1) + 1);
			}
		}
		// calculating the delta (startnode avgeffpar - endnode avgeffpar) for each relationship for a node
		// them calculate the weighted avg of the outgoing edge delta values and store as
		for (Node tnode : nodes) {
			Long curnodeid = tnode.getId();
			if (parcounts.containsKey(curnodeid) == false || avgeffparnums.containsKey(curnodeid) == false) {
				continue;
			}
			int totalweight = 0;
			double total = 0;
			for (Long tndl : parcounts.get(curnodeid).keySet()) {
				totalweight += parcounts.get(curnodeid).get(tndl);
				double st = (avgeffparnums.get(curnodeid).get(0) / avgeffparnums.get(curnodeid).get(1)) - 1;
				double ed = (avgeffparnums.get(tndl).get(0) / avgeffparnums.get(tndl).get(1)) - 1;
				total += ((st - ed) * parcounts.get(curnodeid).get(tndl));
			}
			total /= totalweight;
			avgdelta.put(curnodeid, total);
		}
		System.out.println("nodes traversed");
		Transaction tx = null;
		//nothing calculated beyond, this is just for writing the file
		for(Node tnode: nodes){
			try{
				tx = graphDb.beginTx();

				if(nodesupport.get(tnode.getId())==0){
					continue;
				}
				retstring.append("<node id=\"n"+tnode.getId()+"\">\n");
				if(tnode.hasProperty("name")){
					retstring.append("<data key=\"d0\">"+((String)tnode.getProperty("name")).replace("&", "_")+"</data>\n");
				}
				//not printing the ids as names
				//			else
				//				retstring.append("<data key=\"d0\">"+tnode.getId()+"</data>\n");
				//skip tips
				if(tnode.hasRelationship(Direction.INCOMING, RelTypes.STREECHILDOF)){
					retstring.append("<data key=\"d2\">"+nodesupport.get(tnode.getId())+"</data>\n");
					tnode.setProperty("nodesupport", nodesupport.get(tnode.getId()));
				}
				if(avgeffparnums.containsKey(tnode.getId())){
					double avgeffpar = avgeffparnums.get(tnode.getId()).get(0)/avgeffparnums.get(tnode.getId()).get(1);
					retstring.append("<data key=\"d6\">"+(avgeffpar)+"</data>\n");
					tnode.setProperty("avgeffpar", avgeffpar);
				}
				//else{
				//	retstring.append("<data key=\"d2\">1</data>\n");
				//}
				retstring.append("<data key=\"d4\">"+effpar.get(tnode.getId())+"</data>\n");
				tnode.setProperty("effpar", effpar.get(tnode.getId()));
				retstring.append("<data key=\"d5\">"+effch.get(tnode.getId())+"</data>\n");
				tnode.setProperty("effch", effch.get(tnode.getId()));
				if (avgdelta.containsKey(tnode.getId())==true){
					retstring.append("<data key=\"d7\">"+(avgdelta.get(tnode.getId())*nodesupport.get(tnode.getId()))+"</data>\n");
				}
				retstring.append("</node>\n");
				tx.success();
			}finally{
				tx.finish();
			}
		}
		for (Node tnode : nodes) {
			HashMap<Long, Double> tedgesupport = edgesupport.get(tnode.getId());
			for (Long tl : tedgesupport.keySet()) {
				retstring.append("<edge source=\"n" + tnode.getId() + "\" target=\"n" + tl + "\">\n");
				retstring.append("<data key=\"d3\">" + tedgesupport.get(tl) + "</data>\n");
				// retstring.append("<data key=\"d1\">"+((String)rel.getProperty("source")).replace("&", "_")+"</data>\n");
				retstring.append("</edge>\n");
			}
		}
		System.out.println("nodes written");
		retstring.append("</graph>\n</graphml>\n");
		return retstring.toString();
	}

	/**
	 * This will dump a csv for each of the relationships in the format nodeid,parentid,nodename,parentname,source,brlen
	 * 
	 */
	public void dumpCSV(String startnodes, String outfile, boolean taxonomy) {
		Node startnode = findGraphNodeByName(startnodes);
		if (startnode == null) {
			System.out.println("name not found");
			return;
		}
		try {
			PrintWriter outFile = new PrintWriter(new FileWriter(outfile));
			for (Node tnode : Traversal.description().relationships(RelTypes.MRCACHILDOF, Direction.INCOMING)
					.traverse(startnode).nodes()) {
				for (Relationship trel : tnode.getRelationships(RelTypes.STREECHILDOF)) {
					if (taxonomy == false) {
						if (((String) trel.getProperty("source")).equals("taxonomy"))
							continue;
					}
					outFile.write(trel.getStartNode().getId() + "," + trel.getEndNode().getId() + ",");
					if (trel.getStartNode().hasProperty("name")) {
						outFile.write(((String) trel.getStartNode().getProperty("name")).replace(",", "_"));
					}
					outFile.write(",");
					if (trel.getEndNode().hasProperty("name"))
						outFile.write(((String) trel.getEndNode().getProperty("name")).replace(",", "_"));
					outFile.write("," + trel.getProperty("source") + ",");
					if (trel.hasProperty("branch_length"))
						outFile.write((String) String.valueOf(trel.getProperty("branch_length")));
					outFile.write("\n");
				}
			}
			outFile.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void mrpDump(String taxname, String outfile) throws TaxonNotFoundException  {
		Node firstNode = findTaxNodeByName(taxname);
		String tofile = getMRPDump(firstNode);
		PrintWriter outFile;
		try {
			outFile = new PrintWriter(new FileWriter(outfile));
			outFile.write(tofile);
			outFile.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * This will return the mrp matrix for a node assuming that you only want to look at the tips So it will ignore internal taxonomic names
	 * 
	 * @param startnode
	 * @return string of the mrp matrix
	 */
	private String getMRPDump(Node startnode) {
		HashSet<Long> tids = new HashSet<Long>();
		HashSet<Long> nodeids = new HashSet<Long>();
		HashMap<Long, HashSet<Long>> mrpmap = new HashMap<Long, HashSet<Long>>(); // key is the id for the taxon and the hashset is the list of nodes to which
																				  // the taxon is a member
		long[] dbnodei = (long[]) startnode.getProperty("mrca");
		for (long temp : dbnodei) {
			tids.add(temp);
			mrpmap.put(temp, new HashSet<Long>());
		}
		TraversalDescription STREECHILDOF_TRAVERSAL = Traversal.description()
				.relationships(RelTypes.STREECHILDOF, Direction.INCOMING);
		for (Node tnd : STREECHILDOF_TRAVERSAL.traverse(startnode).nodes()) {
			long[] dbnodet = (long[]) tnd.getProperty("mrca");
			if (dbnodet.length == 1)
				continue;
			for (long temp : dbnodet) {
				mrpmap.get(temp).add(tnd.getId());
			}
			nodeids.add(tnd.getId());
		}
		String retstring = String.valueOf(tids.size()) + " " + String.valueOf(nodeids.size()) + "\n";
		for (Long nd : tids) {
			retstring += (String) graphDb.getNodeById(nd).getProperty("name");
			retstring += "\t";
			for (Long nnid : nodeids) {
				if (mrpmap.get(nd).contains(nnid)) {
					retstring += "1";
				} else {
					retstring += "0";
				}
			}
			retstring += "\n";
		}
		return retstring;
	}

	/*
	 * this constructs a json with tie breaking and puts the alt parents in the assocOBjects for printing
	 * 
	 * need to be guided by some source in order to walk a particular tree works like , "altparents": [{"name": "Adoxaceae",nodeid:"nodeid"},
	 * {"name":"Caprifoliaceae",nodeid:"nodeid"}]
	 */

	public void writeJSONWithAltParentsToFile(String taxname)
				throws TaxonNotFoundException {
		Node firstNode = findTaxNodeByName(taxname);
		if (firstNode == null) {
			System.out.println("name not found");
			return;
		}
		// String tofile = constructJSONAltParents(firstNode);
		ArrayList<Long> alt = new ArrayList<Long>();
		String tofile = constructJSONAltRels(firstNode, null, alt, 3);
		PrintWriter outFile;
		try {
			outFile = new PrintWriter(new FileWriter(taxname + ".json"));
			outFile.write(tofile);
			outFile.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/*
	 * Used for creating a JSON string with a dominant tree, but with alternative parents noted. For now the dominant source is hardcoded for testing. This
	 * needs to be an option once we can list and choose sources
	 */
	public String constructJSONAltParents(Node firstNode) {
		String sourcename = "ATOL_III_ML_CP";
		// sourcename = "dipsacales_matK";
		PathFinder<Path> pf = GraphAlgoFactory.shortestPath(Traversal.pathExpanderForTypes(RelTypes.MRCACHILDOF, Direction.OUTGOING), 100);
		JadeNode root = new JadeNode();
		root.setName((String) firstNode.getProperty("name"));
		TraversalDescription MRCACHILDOF_TRAVERSAL = Traversal.description()
				.relationships(RelTypes.MRCACHILDOF, Direction.INCOMING);
		ArrayList<Node> visited = new ArrayList<Node>();
		ArrayList<Relationship> keepers = new ArrayList<Relationship>();
		HashMap<Node, JadeNode> nodejademap = new HashMap<Node, JadeNode>();
		HashMap<JadeNode, Node> jadeparentmap = new HashMap<JadeNode, Node>();
		visited.add(firstNode);
		nodejademap.put(firstNode, root);
		root.assocObject("nodeid", firstNode.getId());
		for (Node friendnode : MRCACHILDOF_TRAVERSAL.traverse(firstNode).nodes()) {
			// if it is a tip, move back,
			if (friendnode.hasRelationship(Direction.INCOMING, RelTypes.MRCACHILDOF)) {
				continue;
			} else {
				Node curnode = friendnode;
				while (curnode.hasRelationship(Direction.OUTGOING, RelTypes.MRCACHILDOF)) {
					// if it is visited continue
					if (visited.contains(curnode)) {
						break;
					} else {
						JadeNode newnode = new JadeNode();
						if (curnode.hasProperty("name")) {
							newnode.setName((String) curnode.getProperty("name"));
							newnode.setName(newnode.getName().replace("(", "_").replace(")", "_").replace(" ", "_").replace(":", "_"));
						}
						Relationship keep = null;
						for (Relationship rel : curnode.getRelationships(Direction.OUTGOING, RelTypes.STREECHILDOF)) {
							if (keep == null) {
								keep = rel;
							}
							if (((String) rel.getProperty("source")).compareTo(sourcename) == 0) {
								keep = rel;
								break;
							}
							if (pf.findSinglePath(rel.getEndNode(), firstNode) != null || visited.contains(rel.getEndNode())) {
								keep = rel;
							}
						}
						newnode.assocObject("nodeid", curnode.getId());
						ArrayList<Node> conflictnodes = new ArrayList<Node>();
						for (Relationship rel : curnode.getRelationships(Direction.OUTGOING, RelTypes.STREECHILDOF)) {
							if (rel.getEndNode().getId() != keep.getEndNode().getId() && conflictnodes.contains(rel.getEndNode()) == false) {
								// check for nested conflicts
								// if(pf.findSinglePath(keep.getEndNode(), rel.getEndNode())==null)
								conflictnodes.add(rel.getEndNode());
							}
						}
						newnode.assocObject("conflictnodes", conflictnodes);
						nodejademap.put(curnode, newnode);
						visited.add(curnode);
						keepers.add(keep);
						if (pf.findSinglePath(keep.getEndNode(), firstNode) != null) {
							curnode = keep.getEndNode();
							jadeparentmap.put(newnode, curnode);
						} else {
							break;
						}
					}
				}
			}
		}
		for (JadeNode jn : jadeparentmap.keySet()) {
			if (jn.getObject("conflictnodes") != null) {
				String confstr = "";
				@SuppressWarnings("unchecked")
				ArrayList<Node> cn = (ArrayList<Node>) jn.getObject("conflictnodes");
				if (cn.size() > 0) {
					confstr += ", \"altparents\": [";
					for (int i = 0; i < cn.size(); i++) {
						String namestr = "";
						if (cn.get(i).hasProperty("name")) {
							namestr = (String) cn.get(i).getProperty("name");
						}
						confstr += "{\"name\": \"" + namestr + "\",\"nodeid\":\"" + cn.get(i).getId() + "\"}";
						if (i + 1 != cn.size()) {
							confstr += ",";
						}
					}
					confstr += "]\n";
					jn.assocObject("jsonprint", confstr);
				}
			}
			nodejademap.get(jadeparentmap.get(jn)).addChild(jn);
		}
		JadeTree tree = new JadeTree(root);
		root.assocObject("nodedepth", root.getNodeMaxDepth());
		String ret = "[\n";
		ret += tree.getRoot().getJSON(false);
		ret += "]\n";
		return ret;
	}

	/*
	 * This is similar to the JSON alt parents but differs in that it takes a dominant source string, and the alternative relationships in an array.
	 * 
	 * Also this presents a max depth and doesn't show species unless the firstnode is the direct parent of a species
	 * 
	 * Limits the depth to 5
	 * 
	 * Goes back one parent
	 * 
	 * Should work with taxonomy or with the graph and determines this based on relationships around the node
	 */
	public String constructJSONAltRels(Node firstNode,
									   String domsource, 
									   ArrayList<Long> altrels,
									   int maxdepth) {
		cne.setStartNode(firstNode);
		cne.setChildThreshold(200);
		se.setStartNode(firstNode);
		boolean taxonomy = true;
		RelationshipType defaultchildtype = RelTypes.TAXCHILDOF;
		RelationshipType defaultsourcetype = RelTypes.TAXCHILDOF;
		String sourcename = "ncbi";
		if (firstNode.hasRelationship(RelTypes.MRCACHILDOF)) {
			taxonomy = false;
			defaultchildtype = RelTypes.MRCACHILDOF;
			defaultsourcetype = RelTypes.STREECHILDOF;
			sourcename = "ATOL_III_ML_CP";
		}
		if (domsource != null) {
			sourcename = domsource;
		}

		PathFinder<Path> pf = GraphAlgoFactory.shortestPath(Traversal.pathExpanderForTypes(defaultchildtype, Direction.OUTGOING), 100);
		JadeNode root = new JadeNode();
		root.setName((String) firstNode.getProperty("name"));
		TraversalDescription CHILDOF_TRAVERSAL = Traversal.description()
				.relationships(defaultchildtype, Direction.INCOMING);
		ArrayList<Node> visited = new ArrayList<Node>();
		ArrayList<Relationship> keepers = new ArrayList<Relationship>();
		HashMap<Node, JadeNode> nodejademap = new HashMap<Node, JadeNode>();
		HashMap<JadeNode, Node> jadeparentmap = new HashMap<JadeNode, Node>();
		//@diff visited.add(firstNode);
		nodejademap.put(firstNode, root);
		root.assocObject("nodeid", firstNode.getId());
		// These are the altrels that actually made it in the tree
		ArrayList<Long> returnrels = new ArrayList<Long>();
		for (Node friendnode : CHILDOF_TRAVERSAL.depthFirst().evaluator(Evaluators.toDepth(maxdepth)).evaluator(cne).evaluator(se).traverse(firstNode).nodes()) {
			// System.out.println("visiting: "+friendnode.getProperty("name"));
			if (friendnode == firstNode) {
				continue;
			}
			Relationship keep = null;
			Relationship spreferred = null;
			Relationship preferred = null;

			for (Relationship rel : friendnode.getRelationships(Direction.OUTGOING, defaultsourcetype)) {
				if (preferred == null) {
					preferred = rel;
				}
				if (altrels.contains(rel.getId())) {
					keep = rel;
					returnrels.add(rel.getId());
					break;
				} else {
					if (((String) rel.getProperty("source")).compareTo(sourcename) == 0) {
						spreferred = rel;
						break;
					}
					/*
					 * just for last ditch efforts if(pf.findSinglePath(rel.getEndNode(), firstNode) != null || visited.contains(rel.getEndNode())){ preferred =
					 * rel; }
					 */
				}
			}
			if (keep == null) {
				keep = spreferred;// prefer the source rel after an alt
				if (keep == null) {
					continue;// if the node is not part of the main source just continue without making it
					// keep = preferred;//fall back on anything
				}
			}
			JadeNode newnode = new JadeNode();
			if (taxonomy == false) {
				if (friendnode.hasProperty("name")) {
					newnode.setName((String) friendnode.getProperty("name"));
					newnode.setName(newnode.getName().replace("(", "_").replace(")", "_").replace(" ", "_").replace(":", "_"));
				}
			} else {
				newnode.setName(((String) friendnode.getProperty("name")).replace("(", "_").replace(")", "_").replace(" ", "_").replace(":", "_"));
			}

			newnode.assocObject("nodeid", friendnode.getId());

			ArrayList<Relationship> conflictrels = new ArrayList<Relationship>();
			for (Relationship rel : friendnode.getRelationships(Direction.OUTGOING, defaultsourcetype)) {
				if (rel.getEndNode().getId() != keep.getEndNode().getId() && conflictrels.contains(rel) == false) {
					// check for nested conflicts
					// if(pf.findSinglePath(keep.getEndNode(), rel.getEndNode())==null)
					conflictrels.add(rel);
				}
			}
			newnode.assocObject("conflictrels", conflictrels);
			nodejademap.put(friendnode, newnode);
			keepers.add(keep);
			visited.add(friendnode);
			if (firstNode != friendnode && pf.findSinglePath(keep.getStartNode(), firstNode) != null) {
				jadeparentmap.put(newnode, keep.getEndNode());
			}
		}
		// build tree and work with conflicts
		System.out.println("root " + root.getChildCount());
		for (JadeNode jn : jadeparentmap.keySet()) {
			if (jn.getObject("conflictrels") != null) {
				String confstr = "";
				@SuppressWarnings("unchecked")
				ArrayList<Relationship> cr = (ArrayList<Relationship>) jn.getObject("conflictrels");
				if (cr.size() > 0) {
					confstr += ", \"altrels\": [";
					for (int i = 0; i < cr.size(); i++) {
						String namestr = "";
						if (taxonomy == false) {
							if (cr.get(i).getEndNode().hasProperty("name")) {
								namestr = (String) cr.get(i).getEndNode().getProperty("name");
							}
						} else {
							namestr = (String) cr.get(i).getEndNode().getProperty("name");
						}
						confstr += "{\"parentname\": \"" + namestr + "\",\"parentid\":\"" + cr.get(i).getEndNode().getId() + "\",\"altrelid\":\""
								+ cr.get(i).getId() + "\",\"source\":\"" + cr.get(i).getProperty("source") + "\"}";
						if (i + 1 != cr.size()) {
							confstr += ",";
						}
					}
					confstr += "]\n";
					jn.assocObject("jsonprint", confstr);
				}
			}
			try {
				// System.out.println(jn.getName()+" "+nodejademap.get(jadeparentmap.get(jn)).getName());
				nodejademap.get(jadeparentmap.get(jn)).addChild(jn);
			} catch (java.lang.NullPointerException npe) {
				continue;
			}
		}
		System.out.println("root " + root.getChildCount());

		// get the parent so we can move back one node
		Node parFirstNode = null;
		for (Relationship rels : firstNode.getRelationships(Direction.OUTGOING, defaultsourcetype)) {
			if (((String) rels.getProperty("source")).compareTo(sourcename) == 0) {
				parFirstNode = rels.getEndNode();
				break;
			}
		}
		JadeNode beforeroot = new JadeNode();
		if (parFirstNode != null) {
			String namestr = "";
			if (taxonomy == false) {
				if (parFirstNode.hasProperty("name")) {
					namestr = (String) parFirstNode.getProperty("name");
				}
			} else {
				namestr = (String) parFirstNode.getProperty("name");
			}
			beforeroot.assocObject("nodeid", parFirstNode.getId());
			beforeroot.setName(namestr);
			beforeroot.addChild(root);
		} else {
			beforeroot = root;
		}
		beforeroot.assocObject("nodedepth", beforeroot.getNodeMaxDepth());

		// construct the final string
		JadeTree tree = new JadeTree(beforeroot);
		String ret = "[\n";
		ret += tree.getRoot().getJSON(false);
		ret += ",{\"domsource\":\"" + sourcename + "\"}]\n";
		return ret;
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
	}
}
