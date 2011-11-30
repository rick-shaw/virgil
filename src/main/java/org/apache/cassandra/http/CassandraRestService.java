package org.apache.cassandra.http;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

import org.apache.cassandra.http.ext.PATCH;
import org.apache.cassandra.http.mapreduce.CassandraMapReduce;
import org.apache.cassandra.thrift.ConsistencyLevel;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/virgil/")
public class CassandraRestService {
	private static Logger logger = LoggerFactory.getLogger(CassandraRestService.class);
	private CassandraStorage cassandraStorage = null;
	public static final String CONSISTENCY_LEVEL_HEADER = "X-Consistency-Level";

	public CassandraRestService(CassandraStorage cassandraStorage) {
		this.cassandraStorage = cassandraStorage;
	}

	// ================================================================================================================
	// Key Space Operations
	// ================================================================================================================
	@GET
	@Path("/ping")
	@Produces({ "text/plain" })
	public String healthCheck() throws Exception {
		if (logger.isDebugEnabled())
			logger.debug("Ping.");
		cassandraStorage.setKeyspace("system");
		cassandraStorage.getSlice("system", "Versions", "build", ConsistencyLevel.ONE);
		return "OK\n";
	}

	// ================================================================================================================
	// Key Space Operations
	// ================================================================================================================
	@GET
	@Path("/data/")
	@Produces({ "application/json" })
	public String getKeyspaces(@PathParam("keyspace") String keyspace) throws Exception {
		if (logger.isDebugEnabled())
			logger.debug("Listing keyspaces.");
		return cassandraStorage.getKeyspaces();
	}

	@PUT
	@Path("/data/{keyspace}")
	@Produces({ "application/json" })
	public void createKeyspace(@PathParam("keyspace") String keyspace) throws Exception {
		if (logger.isDebugEnabled())
			logger.debug("Creating keyspace [" + keyspace + "]");
		cassandraStorage.addKeyspace(keyspace);
	}

	@DELETE
	@Path("/data/{keyspace}")
	@Produces({ "application/json" })
	public void dropKeyspace(@PathParam("keyspace") String keyspace) throws Exception {
		if (logger.isDebugEnabled())
			logger.debug("Dropping keyspace [" + keyspace + "]");
		cassandraStorage.dropKeyspace(keyspace);
	}

	// ================================================================================================================
	// Column Family Operations
	// ================================================================================================================
	@GET
	@Path("/data/{keyspace}/{columnFamily}")
	@Produces({ "application/json" })
	public String getColumnFamily(@PathParam("keyspace") String keyspace,
			@PathParam("columnFamily") String columnFamily,
			@HeaderParam(CONSISTENCY_LEVEL_HEADER) String consistencyLevel) throws Exception {
		if (logger.isDebugEnabled())
			logger.debug("Creating column family [" + keyspace + "]:[" + columnFamily + "]");
		cassandraStorage.setKeyspace(keyspace);
		return cassandraStorage.getRows(columnFamily, VirgilConfig.getConsistencyLevel(consistencyLevel));
	}

	/*
	 * Adds or updates rows in the column family.
	 */
	@PATCH
	@Path("/data/{keyspace}/{columnFamily}")
	@Produces({ "application/json" })
	public void patchColumnFamily(@PathParam("keyspace") String keyspace,
			@PathParam("columnFamily") String columnFamily, @QueryParam("index") boolean index, String body,
			@HeaderParam(CONSISTENCY_LEVEL_HEADER) String consistencyLevel) throws Exception {
		cassandraStorage.setKeyspace(keyspace);
		JSONObject json = (JSONObject) JSONValue.parse(body);

		if (json == null)
			throw new RuntimeException("Could not parse the JSON object [" + body + "]");

		if (logger.isDebugEnabled())
			logger.debug("Patching column [" + keyspace + "]:[" + columnFamily + "] -> [" + json + "]");

		// TODO: Should probably make this "more atomic" than it is batching
		// everything into a single set of mutations.
		for (Object rowKey : json.keySet()) {
			JSONObject rowJson = (JSONObject) json.get(rowKey);
			String key = (String) rowKey;
			cassandraStorage.setColumn(keyspace, columnFamily, key, rowJson,
					VirgilConfig.getConsistencyLevel(consistencyLevel), index);
		}
	}

	@PUT
	@Path("/data/{keyspace}/{columnFamily}")
	@Produces({ "application/json" })
	public void createColumnFamily(@PathParam("keyspace") String keyspace,
			@PathParam("columnFamily") String columnFamily) throws Exception {
		if (logger.isDebugEnabled())
			logger.debug("Creating column family [" + keyspace + "]:[" + columnFamily + "]");
		cassandraStorage.setKeyspace(keyspace);
		cassandraStorage.createColumnFamily(keyspace, columnFamily);
	}

	@DELETE
	@Path("/data/{keyspace}/{columnFamily}")
	@Produces({ "application/json" })
	public void deleteColumnFamily(@PathParam("keyspace") String keyspace,
			@PathParam("columnFamily") String columnFamily) throws Exception {
		if (logger.isDebugEnabled())
			logger.debug("Deleteing column family [" + keyspace + "]:[" + columnFamily + "]");
		cassandraStorage.setKeyspace(keyspace);
		cassandraStorage.dropColumnFamily(columnFamily);
	}

	// ================================================================================================================
	// Row Operations
	// ================================================================================================================
	/*
	 * Adds or appends to a row, each entry in the JSON map is a column
	 */
	@PATCH
	@Path("/data/{keyspace}/{columnFamily}/{key}")
	@Produces({ "application/json" })
	public void patchRow(@PathParam("keyspace") String keyspace, @PathParam("columnFamily") String columnFamily,
			@PathParam("key") String key, @QueryParam("index") boolean index, String body,
			@HeaderParam(CONSISTENCY_LEVEL_HEADER) String consistencyLevel) throws Exception {
		cassandraStorage.setKeyspace(keyspace);
		JSONObject json = (JSONObject) JSONValue.parse(body);

		if (json == null)
			throw new RuntimeException("Could not parse the JSON object [" + body + "]");

		if (logger.isDebugEnabled())
			logger.debug("Patching column [" + keyspace + "]:[" + columnFamily + "]:[" + key + "] -> [" + json + "]");
		cassandraStorage.setColumn(keyspace, columnFamily, key, json,
				VirgilConfig.getConsistencyLevel(consistencyLevel), index);
	}

	/*
	 * Add or replaces a row, each entry in the JSON map is a column
	 */
	@PUT
	@Path("/data/{keyspace}/{columnFamily}/{key}")
	@Produces({ "application/json" })
	public void setRow(@PathParam("keyspace") String keyspace, @PathParam("columnFamily") String columnFamily,
			@PathParam("key") String key, @QueryParam("index") boolean index, String body,
			@HeaderParam(CONSISTENCY_LEVEL_HEADER) String consistencyLevel) throws Exception {
		cassandraStorage.setKeyspace(keyspace);
		JSONObject json = (JSONObject) JSONValue.parse(body);

		if (json == null)
			throw new RuntimeException("Could not parse the JSON object [" + body + "]");

		if (logger.isDebugEnabled())
			logger.debug("Adding or updating row [" + keyspace + "]:[" + columnFamily + "]:[" + key + "] -> [" + json
					+ "]");

		long deleteTime = this.deleteRow(keyspace, columnFamily, key, index, consistencyLevel);

		cassandraStorage.setColumn(keyspace, columnFamily, key, json,
				VirgilConfig.getConsistencyLevel(consistencyLevel), index, deleteTime + 1);
	}

	/*
	 * Fetches a row
	 */
	@GET
	@Path("/data/{keyspace}/{columnFamily}/{key}")
	@Produces({ "application/json" })
	public String getRow(@PathParam("keyspace") String keyspace, @PathParam("columnFamily") String columnFamily,
			@PathParam("key") String key, @HeaderParam(CONSISTENCY_LEVEL_HEADER) String consistencyLevel)
			throws Exception {
		cassandraStorage.setKeyspace(keyspace);
		if (logger.isDebugEnabled())
			logger.debug("Getting row [" + keyspace + "]:[" + columnFamily + "]:[" + key + "]");

		return cassandraStorage.getSlice(keyspace, columnFamily, key,
				VirgilConfig.getConsistencyLevel(consistencyLevel));
	}

	/*
	 * Deletes a row
	 */
	@DELETE
	@Path("/data/{keyspace}/{columnFamily}/{key}")
	@Produces({ "application/json" })
	public long deleteRow(@PathParam("keyspace") String keyspace, @PathParam("columnFamily") String columnFamily,
			@PathParam("key") String key, @QueryParam("purgeIndex") boolean purgeIndex,
			@HeaderParam(CONSISTENCY_LEVEL_HEADER) String consistencyLevel) throws Exception {
		cassandraStorage.setKeyspace(keyspace);
		if (logger.isDebugEnabled())
			logger.debug("Deleting row [" + keyspace + "]:[" + columnFamily + "]:[" + key + "]");

		return cassandraStorage.deleteRow(keyspace, columnFamily, key,
				VirgilConfig.getConsistencyLevel(consistencyLevel), purgeIndex);
	}

	// ================================================================================================================
	// Column Operations
	// ================================================================================================================

	/*
	 * Fetches a column
	 */
	@GET
	@Path("/data/{keyspace}/{columnFamily}/{key}/{columnName}")
	@Produces({ "application/json" })
	public String getColumn(@PathParam("keyspace") String keyspace, @PathParam("columnFamily") String columnFamily,
			@PathParam("key") String key, @PathParam("columnName") String columnName,
			@HeaderParam(CONSISTENCY_LEVEL_HEADER) String consistencyLevel) throws Exception {
		cassandraStorage.setKeyspace(keyspace);
		if (logger.isDebugEnabled())
			logger.debug("Getting column [" + keyspace + "]:[" + columnFamily + "]:[" + key + "]:[" + columnName + "]");

		return cassandraStorage.getColumn(keyspace, columnFamily, key, columnName,
				VirgilConfig.getConsistencyLevel(consistencyLevel));
	}

	/*
	 * Adds a column
	 */
	@PUT
	@Path("/data/{keyspace}/{columnFamily}/{key}/{columnName}")
	@Produces({ "application/json" })
	public void addColumn(@PathParam("keyspace") String keyspace, @PathParam("columnFamily") String columnFamily,
			@PathParam("key") String key, @PathParam("columnName") String columnName,
			@QueryParam("index") boolean index, String body,
			@HeaderParam(CONSISTENCY_LEVEL_HEADER) String consistencyLevel) throws Exception {
		cassandraStorage.setKeyspace(keyspace);
		if (logger.isDebugEnabled())
			logger.debug("Deleting row [" + keyspace + "]:[" + columnFamily + "]:[" + key + "] => [" + body + "]");
		cassandraStorage.addColumn(keyspace, columnFamily, key, columnName, body,
				VirgilConfig.getConsistencyLevel(consistencyLevel), index);
	}

	/*
	 * Deletes a column
	 */
	@DELETE
	@Path("/data/{keyspace}/{columnFamily}/{key}/{columnName}")
	@Produces({ "application/json" })
	public void deleteColumn(@PathParam("keyspace") String keyspace, @PathParam("columnFamily") String columnFamily,
			@PathParam("key") String key, @PathParam("columnName") String columnName,
			@QueryParam("purgeIndex") boolean purgeIndex, @HeaderParam(CONSISTENCY_LEVEL_HEADER) String consistencyLevel)
			throws Exception {
		cassandraStorage.setKeyspace(keyspace);
		if (logger.isDebugEnabled())
			logger.debug("Deleting row [" + keyspace + "]:[" + columnFamily + "]:[" + key + "]");
		cassandraStorage.deleteColumn(keyspace, columnFamily, key, columnName,
				VirgilConfig.getConsistencyLevel(consistencyLevel), purgeIndex);
	}

	// ================================================================================================================
	// Map Reduce
	// ================================================================================================================

	@POST
	@Path("/job")
	@Produces({ "text/plain" })
	public void patchColumnFamily(@QueryParam("jobName") String jobName,
			@QueryParam("inputKeyspace") String inputKeyspace,
			@QueryParam("inputColumnFamily") String inputColumnFamily,
			@QueryParam("outputKeyspace") String outputKeyspace,
			@QueryParam("outputColumnFamily") String outputColumnFamily, String source) throws Exception {
		if (inputKeyspace == null)
			throw new RuntimeException("Must supply inputKeyspace.");
		if (inputColumnFamily == null)
			throw new RuntimeException("Must supply inputColumnFamily.");
		if (outputKeyspace == null)
			throw new RuntimeException("Must supply outputKeyspace.");
		if (outputColumnFamily == null)
			throw new RuntimeException("Must supply outputColumnFamily.");

		if (logger.isDebugEnabled()) {
			logger.debug("Launching job [" + jobName + "]");
			logger.debug("  --> Input  : Keyspace [" + inputKeyspace + "], ColumnFamily [" + inputColumnFamily + "]");
			logger.debug("  <-- Output : Keyspace [" + outputKeyspace + "], ColumnFamily [" + outputColumnFamily + "]");
		}

		// System.out.println(source);

		CassandraMapReduce.spawn(jobName, VirgilConfig.getCassandraHost(), VirgilConfig.getCassandraPort(),
				inputKeyspace, inputColumnFamily, outputKeyspace, outputColumnFamily, source);
	}
}
