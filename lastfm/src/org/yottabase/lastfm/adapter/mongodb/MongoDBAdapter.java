package org.yottabase.lastfm.adapter.mongodb;

import static java.util.Arrays.asList;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.yottabase.lastfm.core.AbstractDBFacade;
import org.yottabase.lastfm.importer.ListenedTrack;
import org.yottabase.lastfm.importer.User;

import com.mongodb.BasicDBObject;
import com.mongodb.Block;
import com.mongodb.MongoClient;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;



public class MongoDBAdapter extends AbstractDBFacade {
	private MongoClient mongoClient;
	private MongoDatabase db;

	private final static String DATABASE = "lastfm2Normalizzato";
	private final static String COLLECTIONUSERS = "users";
	private final static String COLLECTIONARTISTS = "artists";
	private final static String COLLECTIONLISTENED = "listened";

	public MongoDBAdapter(MongoClient client) {
		this.mongoClient = client;
		db = mongoClient.getDatabase(DATABASE);
	}

	@Override
	public void initializeSchema() {
		db.drop();

		System.out.println("crea schema");

		/*
		 * index options
		 */

		IndexOptions uniqueCostraint = new IndexOptions();
		uniqueCostraint.unique(true);

		IndexOptions noUniqueCostraint = new IndexOptions();
		noUniqueCostraint.unique(false);

		db.getCollection(COLLECTIONARTISTS).createIndex(
				(new Document("artistId", 1)), uniqueCostraint);

		db.getCollection(COLLECTIONUSERS).createIndex(
				(new Document("code", 1)), uniqueCostraint);

		db.getCollection(COLLECTIONLISTENED).createIndex(
				(new Document("code", 1)), noUniqueCostraint);

		db.getCollection(COLLECTIONLISTENED).createIndex(
				(new Document("trackId", 1)), noUniqueCostraint);

	}

	@Override
	public void close() {
		// TODO Auto-generated method stub

	}

	@Override
	public void insertUser(User user) {

		Document userDoc = new Document("code", user.getCode());
		if(user.getGender()!=null)
			userDoc.append("gender", user.getGender());
		if(user.getAge()!=null)
			userDoc.append("age", user.getAge());
		if(user.getCountry()!=null)
			userDoc.append("country", user.getCountry());
		if(user.getSignupDateAsJavaDate()!=null)
			userDoc.append("singupDate", user.getSignupDateAsJavaDate());

		db.getCollection(COLLECTIONUSERS).insertOne(userDoc);
	}

	/*
	 * con user.tracks
	 * @see org.yottabase.lastfm.core.Facade#insertListenedTrack(org.yottabase.lastfm.importer.ListenedTrack)
	 */
	public void insertListenedTrack(ListenedTrack listenedTrack) {

		/*
		 * update artist
		 */
		Bson artist = new Document("artistId", listenedTrack.getArtistCode());

		Bson songTracks = new Document("artistId", listenedTrack.getArtistCode()).append("songs.trackId", listenedTrack.getTrackCode());

		BasicDBObject artistTracks = new BasicDBObject("trackId", listenedTrack.getTrackCode());

		if(listenedTrack.getTrackName()!=null)
			artistTracks.put("trackName", listenedTrack.getTrackName());

		BasicDBObject updateQuery =	new BasicDBObject("$push", new BasicDBObject("songs",artistTracks));

		/*
		 * controlllo esistenza artista
		 */
		FindIterable<Document> artistNotNull = db.getCollection(COLLECTIONARTISTS).find(artist);

		if(artistNotNull.first() != null){
			/*
			 * controllo esistenza track
			 */
			FindIterable<Document> alreadyInsertTracks = db.getCollection(COLLECTIONARTISTS).find(songTracks);
			if(alreadyInsertTracks.first() == null)
				db.getCollection(COLLECTIONARTISTS).findOneAndUpdate(artist,updateQuery);
		}else{
			/*
			 * insert and update 
			 */
			db.getCollection(COLLECTIONARTISTS).insertOne( new Document("artistId", listenedTrack.getArtistCode())
			.append("artistName", listenedTrack.getArtistName()));

			db.getCollection(COLLECTIONARTISTS).updateOne(artist,updateQuery);

		}




		/*
		 * insert listenedTracks users
		 */

		Bson codeTracks = new Document("code", listenedTrack.getCode()).append("trackId", listenedTrack.getTrackCode());

		BasicDBObject time = new BasicDBObject("time", listenedTrack.getTimeAsJavaDate());


		BasicDBObject updateTime =	new BasicDBObject("$push", new BasicDBObject("timeListened",time));

		Document updateListenedTime = db.getCollection(COLLECTIONLISTENED).findOneAndUpdate(codeTracks,updateTime);

		/*
		 * insert and update 
		 */
		if(updateListenedTime == null){
			db.getCollection(COLLECTIONLISTENED).insertOne( new Document("code", listenedTrack.getCode())
			.append("trackId", listenedTrack.getTrackCode()));

			db.getCollection(COLLECTIONLISTENED).updateOne(codeTracks,updateTime);
		}


	}


	@Override
	public void countArtists() {

		this.writer.write(String.valueOf(db.getCollection(COLLECTIONARTISTS).count()));

	}

	@SuppressWarnings("unchecked")
	@Override
	public void countTracks() {
		/*console query
		 * 		db.runCommand ( { distinct: "artists", key: "songs" } )
		 */


		Document explodeTracks = new Document("distinct", "artists").append("key", "songs.trackId");

		Document distinctTracks = db.runCommand(explodeTracks);

		List<String> count = new LinkedList<String>();
		count.addAll((Collection<? extends String>) distinctTracks.get("values"));

		this.writer.write(Integer.toString(count.size()));
		//		System.out.println(count.size());

	}

	@Override
	public void countUsers() {

		this.writer.write(Long.toString(db.getCollection(COLLECTIONUSERS).count()));
		//		System.out.println("user : " + db.getCollection(COLLECTIONUSERS).count());
	}

	@Override
	public void countEntities() {

		//		int countEntities; 

	}

	@Override
	public void averageNumberListenedTracksPerUser(boolean uniqueTrack) {


		if(uniqueTrack){
			/*console query
			 * db.listened.aggregate( [ { $group: { _id: "$code", total: { $sum: 1 } } },{ $group: { _id: null,lngAvg: {$avg: "$total"} }} ] )
			 */

			Document groupByTracks = new Document("$group", new Document("_id", "$code").append("total", new Document("$sum", 1)));
			Document avg = new Document("$group", new Document("_id", null).append("avg", new Document("$avg", "$total")));

			AggregateIterable<Document> iterable = db.getCollection(COLLECTIONLISTENED).aggregate(asList(groupByTracks,avg));
			iterable.forEach(new Block<Document>() {
				public void apply(final Document document) {
					System.out.println(document.toJson());

				}
			});
		}else{
			/*console query
			 * db.listened.aggregate( [ { $project: { "_id":0, "code": 1, "timeListened": { $size: { "$ifNull": [ "$timeListened", [] ] } } } },{ $group: { _id: "$code", total: { $sum: "$timeListened" } } },{ $group: { _id: null,lngAvg: {$avg: "$total"} }} ] )
			 */

			Document groupByTracks = new Document("$project", new Document("code", 1).append("_id", 0).append("timeListened", new Document("$size", new Document( "$ifNull",Arrays.asList("$timeListened",Arrays.asList() ) ))));
			Document groupByUser = new Document("$group", new Document("_id", "$code").append("total", new Document("$sum", "$timeListened")));			
			Document avg = new Document("$group", new Document("_id", null).append("avg", new Document("$avg", "$total")));

			AggregateIterable<Document> iterable = db.getCollection(COLLECTIONLISTENED).aggregate(asList(groupByTracks,groupByUser,avg));
			iterable.forEach(new Block<Document>() {
				public void apply(final Document document) {
					System.out.println(document.toJson());

				}
			});
		}

	}

	@Override
	public void averageNumberSungTracksPerArtist() {

		/*console query
		 * db.artists.aggregate( [ { $project: { "_id":1, "artistId": 1, "total": { $size: "$songs" } } },{$group:{_id: "null",avgQuantity: { $avg: "$total" }}} ] )
		 */

		Document groupByTracks = new Document("$project", new Document("artistId", 1).append("total", new Document("$size", "$songs")));
		Document avg = new Document("$group", new Document("_id", null).append("avg", new Document("$avg", "$total")));

		AggregateIterable<Document> iterable = db.getCollection(COLLECTIONARTISTS).aggregate(asList(groupByTracks,avg));
		iterable.forEach(new Block<Document>() {
			public void apply(final Document document) {
				System.out.println(document.toJson());

			}
		});


	}

	@Override
	public void usersChart(int n, boolean top, boolean uniqueTrack) {
		int order = 1;
		if(top)
			order = -1;

		if(uniqueTrack){
			/*console query
			 * DISTINCT
			 * db.listened.aggregate( [ { $group: { _id: "$code", total: { $sum: 1 } } },{ $sort: { total: 1 }} ],{allowDiskUse: true})
			 */

			Document groupByTracks = new Document("$group", new Document("_id", "$code").append("total", new Document("$sum", 1)));
			Document sort = new Document("$sort", new Document("total",order));
			Document limit = new Document("$limit", n);

			AggregateIterable<Document> iterable = db.getCollection(COLLECTIONLISTENED).aggregate(asList(groupByTracks,sort,limit));
			iterable.forEach(new Block<Document>() {
				public void apply(final Document document) {
					System.out.println(document.toJson());

				}
			});	
		}else{

			/*console query
			 * db.listened.aggregate( [ { $project: { "_id":0, "code": 1, "timeListened": { $size: { "$ifNull": [ "$timeListened", [] ] } } } } ] )
			 */

			Document groupByTracks = new Document("$project", new Document("code", 1).append("_id", 0).append("timeListened", new Document("$size", new Document( "$ifNull",Arrays.asList("$timeListened",Arrays.asList() ) ))));
			Document groupByUser = new Document("$group", new Document("_id", "$code").append("total", new Document("$sum", "$timeListened")));
			Document sort = new Document("$sort", new Document("total",order));
			Document limit = new Document("$limit", n);

			AggregateIterable<Document> iterable = db.getCollection(COLLECTIONLISTENED).aggregate(asList(groupByTracks,groupByUser,sort,limit));
			iterable.forEach(new Block<Document>() {
				public void apply(final Document document) {
					System.out.println(document.toJson());

				}
			});

		}
	}

	@Override
	public void tracksChart(int n, boolean top, boolean uniqueTracks) {
		int order = 1;
		if(top)
			order = -1;

		if(uniqueTracks){
			Document groupByTracks = new Document("$group", new Document("_id", "$trackId").append("total", new Document("$sum", 1)));
			Document sort = new Document("$sort", new Document("total",order));
			Document limit = new Document("$limit", n);

			AggregateIterable<Document> iterable = db.getCollection(COLLECTIONLISTENED).aggregate(asList(groupByTracks,sort,limit));

			iterable.forEach(new Block<Document>() {
				public void apply(final Document document) {
					System.out.println(document.toJson());

				}
			});
		}else{

			/*console query
			 * db.listened.aggregate( [ { $project: { "_id":0, "code": 1, "timeListened": { $size: { "$ifNull": [ "$timeListened", [] ] } } } } ] )
			 */

			Document groupByTracks = new Document("$project", new Document("trackId", 1).append("_id", 0).append("timeListened", new Document("$size", new Document( "$ifNull",Arrays.asList("$timeListened",Arrays.asList() ) ))));
			Document groupByUser = new Document("$group", new Document("_id", "$trackId").append("total", new Document("$sum", "$timeListened")));
			Document sort = new Document("$sort", new Document("total",order));
			Document limit = new Document("$limit", n);

			AggregateIterable<Document> iterable = db.getCollection(COLLECTIONLISTENED).aggregate(asList(groupByTracks,groupByUser,sort,limit));
			iterable.forEach(new Block<Document>() {
				public void apply(final Document document) {
					System.out.println(document.toJson());

				}
			});

		}
	}

	@Override

	public void artistsChart(int n, boolean top, boolean uniqueTracks) {
		int order = 1;
		if(top)
			order = -1;


		if(uniqueTracks){
			Document groupByTracks = new Document("$group", new Document("_id", "$trackId").append("total", new Document("$sum", 1)));
			Document sort = new Document("$sort", new Document("total",order));
			Document limit = new Document("$limit", n);

			AggregateIterable<Document> iterable = db.getCollection(COLLECTIONLISTENED).aggregate(asList(groupByTracks,sort,limit));

			iterable.forEach(new Block<Document>() {
				public void apply(final Document document) {

					/*console query
					 * db.artists.aggregate( [ { $match: { "songs.trackId": "1c061863-1d3e-4066-aa93-5c9ce0bf72f2" } }, { $project: { "_id":0, "artistId": 1, "artistName": 1 } } ] )
					 */

					Document groupUser = new Document("$match", new Document("songs.trackId", document.get("_id")));
					Document projection = new Document("$project", new Document("_id", 0).append("artistId",1).append("artistName", 1));

					AggregateIterable<Document> iterableArtist = db.getCollection(COLLECTIONARTISTS).aggregate(asList(groupUser,projection));
					iterableArtist.forEach(new Block<Document>() {
						public void apply(final Document document2) {
							System.out.println(document2.toJson()+ " count : " + document.get("total"));

						}
					});

				}
			});	
		}else{
			/*console query
			 * db.listened.aggregate( [ { $project: { "_id":0, "code": 1, "timeListened": { $size: { "$ifNull": [ "$timeListened", [] ] } } } } ] )
			 */

			Document groupByTracks = new Document("$project", new Document("trackId", 1).append("_id", 0).append("timeListened", new Document("$size", new Document( "$ifNull",Arrays.asList("$timeListened",Arrays.asList() ) ))));
			Document groupByUser = new Document("$group", new Document("_id", "$trackId").append("total", new Document("$sum", "$timeListened")));
			Document sort = new Document("$sort", new Document("total",order));
			Document limit = new Document("$limit", n);

			AggregateIterable<Document> iterable = db.getCollection(COLLECTIONLISTENED).aggregate(asList(groupByTracks,groupByUser,sort,limit));
			iterable.forEach(new Block<Document>() {
				public void apply(final Document document) {

					/*console query
					 * db.artists.aggregate( [ { $match: { "songs.trackId": "1c061863-1d3e-4066-aa93-5c9ce0bf72f2" } }, { $project: { "_id":0, "artistId": 1, "artistName": 1 } } ] )
					 */

					Document groupUser = new Document("$match", new Document("songs.trackId", document.get("_id")));
					Document projection = new Document("$project", new Document("_id", 0).append("artistId",1).append("artistName", 1));

					AggregateIterable<Document> iterableArtist = db.getCollection(COLLECTIONARTISTS).aggregate(asList(groupUser,projection));
					iterableArtist.forEach(new Block<Document>() {
						public void apply(final Document document2) {
							System.out.println(document2.toJson()+ " count : " + document.get("total"));

						}
					});

				}
			});	
		}

	}

	@Override
	public void artistByCode(String artistCode) {

		FindIterable<Document> iterable = db.getCollection(COLLECTIONARTISTS).find(
				new Document("artistId", artistCode));
		System.out.println(iterable.first().toJson());

	}

	@Override
	public void artistByName(String artistName) {

		FindIterable<Document> iterable = db.getCollection(COLLECTIONARTISTS).find(
				new Document("artistName", artistName));
		System.out.println(iterable.first().toJson());

	}

	@Override
	public void usersByAgeRange(int lowerBound, int upperBound) {

		// db.users.find( { age: { $gt: 10, $lt: 40 } } );
		FindIterable<Document> iterable = db.getCollection(COLLECTIONUSERS).find(
				new Document("age", new Document("$gt", lowerBound).append("$lt", upperBound)));

		iterable.forEach(new Block<Document>() {
			@Override
			public void apply(final Document document) {
				System.out.println(document.toJson());
			}
		});

	}

	@Override
	public void tracksSungByArtist(String artistCode) {
		// db.artists.aggregate( [ { $match: { artistId: '6685bdd9-37f3-4323-8f7c-0e7bb9103e6d' } },{ $unwind : "$songs" },{ $project: { "_id":0, "songs": 1 } }])

		Document match = new Document("$match", new Document("artistId", artistCode));
		Document unwind = new Document("$unwind", "$songs");
		Document project = new Document("$project", new Document("_id", 0).append("songs", 1));

		AggregateIterable<Document> iterable = db.getCollection(COLLECTIONARTISTS).aggregate(asList(match,unwind,project));

		iterable.forEach(new Block<Document>() {
			public void apply(final Document document) {
				System.out.println(document.toJson());

			}
		});

	}

	@Override
	public void usersCountByCountry() {
		// db.users.aggregate( [{ $group: { _id: "$country", total: { $sum: 1 } } } ])

		Document groupByCountry = new Document("$group", new Document("_id", "$country").append("total", new Document("$sum",1)));
		Document sort = new Document("$sort", new Document("total",-1));
		Document limit = new Document("$limit", 10);
		AggregateIterable<Document> iterable = db.getCollection(COLLECTIONUSERS).aggregate(asList(groupByCountry,sort,limit));

		iterable.forEach(new Block<Document>() {
			public void apply(final Document document) {
				System.out.println(document.toJson());

			}
		});
	}

	@Override
	public void usersCountByCountryAndGender() {
		/*console query
		 * db.users.aggregate( [ { $group: { _id: {"country": "$country","gender": "$gender"}, total: { $sum: 1 } } } ])
		 */
		
		Document groupByTracks = new Document("$group", new Document("_id", new Document("country","$country").append("gender", "$gender")).append("total", new Document("$sum", 1)));
		Document sort = new Document("$sort", new Document("total",-1));
		Document limit = new Document("$limit", 10);

		AggregateIterable<Document> iterable = db.getCollection(COLLECTIONUSERS).aggregate(asList(groupByTracks,sort,limit));
		iterable.forEach(new Block<Document>() {
			public void apply(final Document document) {
				System.out.println(document.toJson());

			}
		});	
		
		

	}

}