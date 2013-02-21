package opentree;

import jade.tree.JadeNode;
import jade.tree.JadeTree;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

import opentree.GraphBase.RelTypes;

import org.neo4j.graphalgo.GraphAlgoFactory;
import org.neo4j.graphalgo.PathFinder;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.traversal.Evaluators;
import org.neo4j.graphdb.traversal.TraversalDescription;
import org.neo4j.kernel.EmbeddedGraphDatabase;
import org.neo4j.kernel.Traversal;

public class GraphExporter extends GraphBase{

	private SpeciesEvaluator se;
	private ChildNumberEvaluator cne;
	private TaxaListEvaluator tle;
	
	public GraphExporter(){
		cne = new ChildNumberEvaluator();
		cne.setChildThreshold(100);
		se = new SpeciesEvaluator();
		tle = new TaxaListEvaluator();
	}
	
	public GraphExporter(String graphname){
		cne = new ChildNumberEvaluator();
		cne.setChildThreshold(100);
		se = new SpeciesEvaluator();
		tle = new TaxaListEvaluator();
		graphDb = new EmbeddedGraphDatabase( graphname );
		graphNodeIndex = graphDb.index().forNodes( "graphNamedNodes" );
		sourceRelIndex = graphDb.index().forRelationships("sourceRels");
		sourceRootIndex = graphDb.index().forNodes("sourceRootNodes");
	}
	
	public GraphExporter(EmbeddedGraphDatabase graphn){
		graphDb = graphn;
		graphNodeIndex = graphDb.index().forNodes( "graphNamedNodes" );
		sourceRelIndex = graphDb.index().forRelationships("sourceRels");
		sourceRootIndex = graphDb.index().forNodes("sourceRootNodes");
	}

	public void writeGraphML(String taxname, String outfile){
		Node firstNode = findTaxNodeByName(taxname);
		String tofile = getGraphML(firstNode);
		PrintWriter outFile;
		try {
			outFile = new PrintWriter(new FileWriter(outfile));
			outFile.write(tofile);
			outFile.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private String getGraphML(Node startnode){
		String retstring = "<graphml>\n";
		retstring += "<key id=\"d0\" for=\"node\" attr.name=\"taxon\" attr.type=\"string\">\n";
	    retstring += "<default></default>\n";
	    retstring += "</key>\n";
	    retstring += "<key id=\"d1\" for=\"edge\" attr.name=\"sourcename\" attr.type=\"string\">\n";
	    retstring += "<default></default>\n";
	    retstring += "</key>\n";
	    retstring += "<graph id=\"G\" edgedefault=\"directed\">\n";
		HashSet<Node> nodes = new HashSet<Node>();
		for(Node tnode:  Traversal.description().relationships(RelTypes.STREECHILDOF, Direction.INCOMING)
				.traverse(startnode).nodes()){
			nodes.add(tnode);
		}
		System.out.println("nodes traversed: "+nodes.size());
		//could do this in one loop but it is just cleaner to read like this for now
		Iterator<Node> itn = nodes.iterator();
		while(itn.hasNext()){
			Node nxt = itn.next();
			retstring += "<node id=\"n"+nxt.getId()+"\">\n";
			if(nxt.hasProperty("name"))
				retstring += "<data key=\"d0\">"+((String)nxt.getProperty("name")).replace("&", "_")+"</data>\n";
			else
				retstring += "<data key=\"d0\">"+nxt.getId()+"</data>\n";
			retstring += "</node>\n";
		}
		System.out.println("nodes written");
		itn = nodes.iterator();
		while(itn.hasNext()){
			Node nxt = itn.next();
			for(Relationship rel: nxt.getRelationships(RelTypes.STREECHILDOF, Direction.INCOMING)){
				if(nodes.contains(rel.getStartNode()) && nodes.contains(rel.getEndNode()) //){
						&& 
						((String)rel.getProperty("source")).compareTo("taxonomy") != 0){
					retstring += "<edge source=\"n"+rel.getStartNode().getId()+"\" target=\"n"+rel.getEndNode().getId()+"\">\n"; 
					retstring += "<data key=\"d1\">"+((String)rel.getProperty("source")).replace("&", "_")+"</data>\n";
					retstring += "</edge>\n";
				}
			}
		}
		System.out.println("edges written");
		retstring += "</graph>\n</graphml>\n";
		return retstring;
	}
	
	/**
	 * This will dump a csv for each of the relationships in the format 
	 * nodeid,parentid,nodename,parentname,source,brlen 
	 * 
	 */
	public void dumpCSV(String startnodes,String outfile,boolean taxonomy){
		Node startnode = findGraphNodeByName(startnodes);
		if(startnode == null){
			System.out.println("name not found");
			return;
		}
		try{
			PrintWriter outFile = new PrintWriter(new FileWriter(outfile));
			for(Node tnode:  Traversal.description().relationships(RelTypes.MRCACHILDOF, Direction.INCOMING)
				.traverse(startnode).nodes()){
				for(Relationship trel: tnode.getRelationships(RelTypes.STREECHILDOF)){
					if(taxonomy == false){
						if (((String)trel.getProperty("source")).equals("taxonomy"))
							continue;
					}
					outFile.write(trel.getStartNode().getId() +","+trel.getEndNode().getId()+",");
					if(trel.getStartNode().hasProperty("name")){
						outFile.write(((String)trel.getStartNode().getProperty("name")).replace(",","_"));
					}
					outFile.write(",");
					if(trel.getEndNode().hasProperty("name"))
						outFile.write(((String)trel.getEndNode().getProperty("name")).replace(",","_"));
					outFile.write(","+trel.getProperty("source")+",");
					if(trel.hasProperty("branch_length"))
						outFile.write((String)String.valueOf(trel.getProperty("branch_length")));
					outFile.write("\n");
				}
			}
			outFile.close();
		}catch(IOException e){
			e.printStackTrace();
		}
	}
	
	public void mrpDump(String taxname, String outfile){
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
	 * This will return the mrp matrix for a node assuming that you only want to look at the tips
	 * So it will ignore internal taxonomic names
	 * @param startnode
	 * @return string of the mrp matrix
	 */
	private String getMRPDump(Node startnode){
		HashSet<Long> tids = new HashSet<Long>();
		HashSet<Long> nodeids = new HashSet<Long>();
		HashMap<Long,HashSet<Long>> mrpmap = new HashMap<Long,HashSet<Long>>(); //key is the id for the taxon and the hashset is the list of nodes to which the taxon is a member
		long [] dbnodei = (long []) startnode.getProperty("mrca");
		for(long temp:dbnodei){tids.add(temp);mrpmap.put(temp, new HashSet<Long>());}
		TraversalDescription STREECHILDOF_TRAVERSAL = Traversal.description()
		        .relationships( RelTypes.STREECHILDOF,Direction.INCOMING );
		for(Node tnd:STREECHILDOF_TRAVERSAL.traverse(startnode).nodes()){
			long [] dbnodet = (long []) tnd.getProperty("mrca");
			if (dbnodet.length == 1)
				continue;
			for(long temp:dbnodet){
				mrpmap.get(temp).add(tnd.getId());
			}
			nodeids.add(tnd.getId());
		}
		String retstring = String.valueOf(tids.size())+" "+String.valueOf(nodeids.size())+"\n";
		for(Long nd: tids){
			retstring += (String)graphDb.getNodeById(nd).getProperty("name");
			retstring += "\t";
			for(Long nnid: nodeids){
				if (mrpmap.get(nd).contains(nnid)){
					retstring += "1";
				}else{
					retstring += "0";
				}
			}
			retstring += "\n";
		}
		return retstring;
	}

	

	/*
	 * this constructs a json with tie breaking and puts the alt parents
	 * in the assocOBjects for printing
	 * 
	 * need to be guided by some source in order to walk a particular tree
	 * works like , "altparents": [{"name": "Adoxaceae",nodeid:"nodeid"}, {"name":"Caprifoliaceae",nodeid:"nodeid"}]
	 */
	
	public void writeJSONWithAltParentsToFile(String taxname){
        Node firstNode = findTaxNodeByName(taxname);
		if(firstNode == null){
			System.out.println("name not found");
			return;
		}
//		String tofile = constructJSONAltParents(firstNode);
		ArrayList<Long>alt = new ArrayList<Long>();
		String tofile = constructJSONAltRels(firstNode,null,alt);
		PrintWriter outFile;
		try {
			outFile = new PrintWriter(new FileWriter(taxname+".json"));
			outFile.write(tofile);
			outFile.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/*
	 * Used for creating a JSON string with a dominant tree, but with alternative 
	 * parents noted.
	 * For now the dominant source is hardcoded for testing. This needs to be an option
	 * once we can list and choose sources
	 */
	public String constructJSONAltParents(Node firstNode){
		String sourcename = "ATOL_III_ML_CP"; 
//		sourcename = "dipsacales_matK";
		PathFinder <Path> pf = GraphAlgoFactory.shortestPath(Traversal.pathExpanderForTypes(RelTypes.MRCACHILDOF, Direction.OUTGOING), 100);
		JadeNode root = new JadeNode();
		root.setName((String)firstNode.getProperty("name"));
		TraversalDescription MRCACHILDOF_TRAVERSAL = Traversal.description()
		        .relationships( RelTypes.MRCACHILDOF,Direction.INCOMING );
		ArrayList<Node> visited = new ArrayList<Node>();
		ArrayList<Relationship> keepers = new ArrayList<Relationship>();
		HashMap<Node,JadeNode> nodejademap = new HashMap<Node,JadeNode>();
		HashMap<JadeNode,Node> jadeparentmap = new HashMap<JadeNode,Node>();
		visited.add(firstNode);
		nodejademap.put(firstNode, root);
		root.assocObject("nodeid", firstNode.getId());
		for(Node friendnode : MRCACHILDOF_TRAVERSAL.traverse(firstNode).nodes()){
			//if it is a tip, move back, 
			if(friendnode.hasRelationship(Direction.INCOMING, RelTypes.MRCACHILDOF))
				continue;
			else{
				Node curnode = friendnode;
				while(curnode.hasRelationship(Direction.OUTGOING, RelTypes.MRCACHILDOF)){
					//if it is visited continue
					if (visited.contains(curnode)){
						break;
					}else{
						JadeNode newnode = new JadeNode();
						if(curnode.hasProperty("name")){
							newnode.setName((String)curnode.getProperty("name"));
							newnode.setName(newnode.getName().replace("(", "_").replace(")","_").replace(" ", "_").replace(":", "_"));
						}
						Relationship keep = null;
						for(Relationship rel: curnode.getRelationships(Direction.OUTGOING, RelTypes.STREECHILDOF)){
							if(keep == null)
								keep = rel;
							if (((String)rel.getProperty("source")).compareTo(sourcename) == 0){
								keep = rel;
								break;
							}
							if(pf.findSinglePath(rel.getEndNode(), firstNode) != null || visited.contains(rel.getEndNode())){
								keep = rel;
							}
						}
						newnode.assocObject("nodeid", curnode.getId());
						ArrayList<Node> conflictnodes = new ArrayList<Node>();
						for(Relationship rel:curnode.getRelationships(Direction.OUTGOING, RelTypes.STREECHILDOF)){
							if(rel.getEndNode().getId() != keep.getEndNode().getId() && conflictnodes.contains(rel.getEndNode())==false){
								//check for nested conflicts
	//							if(pf.findSinglePath(keep.getEndNode(), rel.getEndNode())==null)
									conflictnodes.add(rel.getEndNode());
							}
						}
						newnode.assocObject("conflictnodes", conflictnodes);
						nodejademap.put(curnode, newnode);
						visited.add(curnode);
						keepers.add(keep);
						if(pf.findSinglePath(keep.getEndNode(), firstNode) != null){
							curnode = keep.getEndNode();
							jadeparentmap.put(newnode, curnode);
						}else
							break;
					}
				}
			}
		}
		for(JadeNode jn:jadeparentmap.keySet()){
			if(jn.getObject("conflictnodes")!=null){
				String confstr = "";
				@SuppressWarnings("unchecked")
				ArrayList<Node> cn = (ArrayList<Node>)jn.getObject("conflictnodes");
				if(cn.size()>0){
					confstr += ", \"altparents\": [";
					for(int i=0;i<cn.size();i++){
						String namestr = "";
						if(cn.get(i).hasProperty("name"))
							namestr = (String) cn.get(i).getProperty("name");
						confstr += "{\"name\": \""+namestr+"\",\"nodeid\":\""+cn.get(i).getId()+"\"}";
						if(i+1 != cn.size())
							confstr += ",";
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
	 * This is similar to the JSON alt parents but differs in that it takes
	 * a dominant source string, and the alternative relationships in an
	 * array.
	 * 
	 * Also this presents a max depth and doesn't show species unless the firstnode 
	 * is the direct parent of a species
	 * 
	 * Limits the depth to 5
	 * 
	 * Goes back one parent
	 * 
	 * Should work with taxonomy or with the graph and determines this based on relationships
	 * around the node
	 */
	public String constructJSONAltRels(Node firstNode, String domsource, ArrayList<Long> altrels){
		cne.setStartNode(firstNode);
		cne.setChildThreshold(200);
		se.setStartNode(firstNode);
		int maxdepth = 3;
		boolean taxonomy = true;
		RelationshipType defaultchildtype = RelTypes.TAXCHILDOF;
		RelationshipType defaultsourcetype = RelTypes.TAXCHILDOF;
		String sourcename = "ncbi";
		if(firstNode.hasRelationship(RelTypes.MRCACHILDOF)){
			taxonomy = false;
			defaultchildtype = RelTypes.MRCACHILDOF;
			defaultsourcetype = RelTypes.STREECHILDOF;
			sourcename = "ATOL_III_ML_CP";
		}
		if(domsource != null)
			sourcename = domsource;

		PathFinder <Path> pf = GraphAlgoFactory.shortestPath(Traversal.pathExpanderForTypes(defaultchildtype, Direction.OUTGOING), 100);
		JadeNode root = new JadeNode();
		if(taxonomy == false)
			root.setName((String)firstNode.getProperty("name"));
		else
			root.setName((String)firstNode.getProperty("name"));
		TraversalDescription CHILDOF_TRAVERSAL = Traversal.description()
		        .relationships( defaultchildtype,Direction.INCOMING );
		ArrayList<Node> visited = new ArrayList<Node>();
		ArrayList<Relationship> keepers = new ArrayList<Relationship>();
		HashMap<Node,JadeNode> nodejademap = new HashMap<Node,JadeNode>();
		HashMap<JadeNode,Node> jadeparentmap = new HashMap<JadeNode,Node>();
		nodejademap.put(firstNode, root);
		root.assocObject("nodeid", firstNode.getId());
		//These are the altrels that actually made it in the tree
		ArrayList<Long> returnrels = new ArrayList<Long>();
		for(Node friendnode : CHILDOF_TRAVERSAL.depthFirst().evaluator(Evaluators.toDepth(maxdepth)).evaluator(cne).evaluator(se).traverse(firstNode).nodes()){
//			System.out.println("visiting: "+friendnode.getProperty("name"));
			if (friendnode == firstNode)
				continue;
			Relationship keep = null;
			Relationship spreferred = null;
			Relationship preferred = null;
			
			for(Relationship rel: friendnode.getRelationships(Direction.OUTGOING, defaultsourcetype)){
				if(preferred == null)
					preferred = rel;
				if(altrels.contains(rel.getId())){
					keep = rel;
					returnrels.add(rel.getId());
					break;
				}else{
					if (((String)rel.getProperty("source")).compareTo(sourcename) == 0){
						spreferred = rel;
						break;
					}
					/*just for last ditch efforts
					 * if(pf.findSinglePath(rel.getEndNode(), firstNode) != null || visited.contains(rel.getEndNode())){
						preferred = rel;
					}*/
				}
			}
			if(keep == null){
				keep = spreferred;//prefer the source rel after an alt
				if(keep == null){
					continue;//if the node is not part of the main source just continue without making it
//					keep = preferred;//fall back on anything
				}
			}
			JadeNode newnode = new JadeNode();
			if(taxonomy == false){
				if(friendnode.hasProperty("name")){
					newnode.setName((String)friendnode.getProperty("name"));
					newnode.setName(newnode.getName().replace("(", "_").replace(")","_").replace(" ", "_").replace(":", "_"));
				}
			}else{
				newnode.setName(((String)friendnode.getProperty("name")).replace("(", "_").replace(")","_").replace(" ", "_").replace(":", "_"));
			}

			newnode.assocObject("nodeid", friendnode.getId());
			
			ArrayList<Relationship> conflictrels = new ArrayList<Relationship>();
			for(Relationship rel:friendnode.getRelationships(Direction.OUTGOING, defaultsourcetype)){
				if(rel.getEndNode().getId() != keep.getEndNode().getId() && conflictrels.contains(rel)==false){
					//check for nested conflicts
					//							if(pf.findSinglePath(keep.getEndNode(), rel.getEndNode())==null)
					conflictrels.add(rel);
				}
			}
			newnode.assocObject("conflictrels",conflictrels);
			nodejademap.put(friendnode, newnode);
			keepers.add(keep);
			visited.add(friendnode);
			if(firstNode != friendnode && pf.findSinglePath(keep.getStartNode(), firstNode) != null){
				jadeparentmap.put(newnode, keep.getEndNode());
			}
		}
		//build tree and work with conflicts
		System.out.println("root "+root.getChildCount());
		for(JadeNode jn:jadeparentmap.keySet()){
			if(jn.getObject("conflictrels")!=null){
				String confstr = "";
				@SuppressWarnings("unchecked")
				ArrayList<Relationship> cr = (ArrayList<Relationship>)jn.getObject("conflictrels");
				if(cr.size()>0){
					confstr += ", \"altrels\": [";
					for(int i=0;i<cr.size();i++){
						String namestr = "";
						if(taxonomy == false){
							if(cr.get(i).getEndNode().hasProperty("name"))
								namestr = (String) cr.get(i).getEndNode().getProperty("name");
						}else{
							namestr = (String)cr.get(i).getEndNode().getProperty("name");
						}
						confstr += "{\"parentname\": \""+namestr+"\",\"parentid\":\""+cr.get(i).getEndNode().getId()+"\",\"altrelid\":\""+cr.get(i).getId()+"\",\"source\":\""+cr.get(i).getProperty("source")+"\"}";
						if(i+1 != cr.size())
							confstr += ",";
					}
					confstr += "]\n";
					jn.assocObject("jsonprint", confstr);
				}
			}
			try{
//				System.out.println(jn.getName()+" "+nodejademap.get(jadeparentmap.get(jn)).getName());
				nodejademap.get(jadeparentmap.get(jn)).addChild(jn);
			}catch(java.lang.NullPointerException npe){
				continue;
			}
		}
		System.out.println("root "+root.getChildCount());
		
		//get the parent so we can move back one node
		Node parFirstNode = null;
		for(Relationship rels : firstNode.getRelationships(Direction.OUTGOING, defaultsourcetype)){
			if(((String)rels.getProperty("source")).compareTo(sourcename) == 0){
				parFirstNode = rels.getEndNode();
				break;
			}
		}
		JadeNode beforeroot = new JadeNode();
		if(parFirstNode != null){
			String namestr = "";
			if(taxonomy == false){
				if(parFirstNode.hasProperty("name"))
					namestr = (String) parFirstNode.getProperty("name");
			}else{
				namestr = (String)parFirstNode.getProperty("name");
			}
			beforeroot.assocObject("nodeid", parFirstNode.getId());
			beforeroot.setName(namestr);
			beforeroot.addChild(root);
		}else{
			beforeroot = root;
		}
		beforeroot.assocObject("nodedepth", beforeroot.getNodeMaxDepth());
		
		//construct the final string
		JadeTree tree = new JadeTree(beforeroot);
		String ret = "[\n";
		ret += tree.getRoot().getJSON(false);
		ret += ",{\"domsource\":\""+sourcename+"\"}]\n";
		return ret;
	}
	
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		

	}

}
