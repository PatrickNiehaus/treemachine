package opentree.synthesis;

import org.neo4j.graphdb.Relationship;

/**
 * The class provides access to conflict resolution methods.
 * 
 * @author cody hinchliff
 *
 */
public class RelationshipConflictResolver {

	private ResolutionMethod method;
	
	public RelationshipConflictResolver(ResolutionMethod method) {
		this.method = method;
	}
	
	public RelationshipConflictResolver setResolutionMethod (ResolutionMethod method) {
		this.method = method;
		return this;
	}
	
	public ResolutionMethod getResolutionMethod () {
		return method;
	}
	
	public Iterable<Relationship> resolveConflicts(Iterable<Relationship> candidateRels) {
		return method.resolveConflicts(candidateRels);
	}
	
	public String getDescription() {
		return "Conflicts resolution will " + method.getDescription();
	}
}
