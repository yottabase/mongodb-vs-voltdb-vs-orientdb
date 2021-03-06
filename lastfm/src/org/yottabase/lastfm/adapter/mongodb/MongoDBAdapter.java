package org.yottabase.lastfm.adapter.mongodb;

import static java.util.Arrays.asList;

import java.util.ArrayList;
import java.util.Arrays;

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

	private final static String DATABASE = "lastfm";
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
		writer.close();

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

	@Override
	public void countTracks() {
		/*
		 * console query
		 * db.artists.aggregate( [ { $project: { "_id":1, "artistId": 1, "total": { $size: "$songs" } } },{$group:{_id: "null",tot: { $sum: "$total" }}} ] )
		 */
		Document groupByTracks = new Document("$project", new Document("artistId", 1).append("total", new Document("$size", "$songs")));
		Document sum = new Document("$group", new Document("_id", null).append("tot", new Document("$sum", "$total")));

		AggregateIterable<Document> iterable = db.getCollection(COLLECTIONARTISTS).aggregate(asList(groupByTracks,sum));
		iterable.forEach(new Block<Document>() {
			public void apply(final Document document) {

				writer.write(document.get("tot").toString());

			}
		});

	}

	@Override
	public void countUsers() {
		
		this.writer.write(Long.toString(db.getCollection(COLLECTIONUSERS).count()));
	}

	@Override
	public void countEntities() {

		long artist = db.getCollection(COLLECTIONARTISTS).count();
		long user = db.getCollection(COLLECTIONUSERS).count();
		
		Document groupByTracks = new Document("$project", new Document("artistId", 1).append("total", new Document("$size", "$songs")));
		Document sum = new Document("$group", new Document("_id", null).append("tot", new Document("$sum", "$total")));

		AggregateIterable<Document> iterable = db.getCollection(COLLECTIONARTISTS).aggregate(asList(groupByTracks,sum));
		iterable.forEach(new Block<Document>() {
			public void apply(final Document document) {
				Long track = Long.valueOf(document.get("tot").toString());
				long total = artist+user+track;
				writer.write(Long.toString(total));	

			}
		});
		
	}

	@Override
	public void averageNumberListenedTracksPerUser() {

		/*console query
		 * db.listened.aggregate( [ { $project: { "_id":0, "code": 1, "timeListened": { $size: { "$ifNull": [ "$timeListened", [] ] } } } },{ $group: { _id: "$code", total: { $sum: "$timeListened" } } },{ $group: { _id: null,lngAvg: {$avg: "$total"} }} ] )
		 */

		Document groupByTracks = new Document("$project", new Document("code", 1).append("_id", 0).append("timeListened", new Document("$size", new Document( "$ifNull",Arrays.asList("$timeListened",Arrays.asList() ) ))));
		Document groupByUser = new Document("$group", new Document("_id", "$code").append("total", new Document("$sum", "$timeListened")));			
		Document avg = new Document("$group", new Document("_id", null).append("avg", new Document("$avg", "$total")));

		AggregateIterable<Document> iterable = db.getCollection(COLLECTIONLISTENED).aggregate(asList(groupByTracks,groupByUser,avg));
		iterable.forEach(new Block<Document>() {
			public void apply(final Document document) {

				writer.write(document.get("avg").toString());

			}
		});

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

				writer.write(document.get("avg").toString());

			}
		});


	}

	@Override
	public void usersChart(int n, boolean top) {
		int order = 1;
		if(top)
			order = -1;

		/*console query
		 * db.listened.aggregate( [ { $project: { "_id":0, "code": 1, "timeListened": { $size: { "$ifNull": [ "$timeListened", [] ] } } } } ] )
		 */
		//user_code", "gender", "age", "country", "signup_date","total"

		Document groupByTracks = new Document("$project", new Document("code", 1).append("_id", 0).append("timeListened", new Document("$size", new Document( "$ifNull",Arrays.asList("$timeListened",Arrays.asList() ) ))));
		Document groupByUser = new Document("$group", new Document("_id", "$code").append("total", new Document("$sum", "$timeListened")));
		Document sort = new Document("$sort", new Document("total",order));
		Document limit = new Document("$limit", n);

		AggregateIterable<Document> iterable = db.getCollection(COLLECTIONLISTENED).aggregate(asList(groupByTracks,groupByUser,sort,limit));
		iterable.forEach(new Block<Document>() {
			public void apply(final Document document) {
				FindIterable<Document> join = db.getCollection(COLLECTIONUSERS).find(new Document("code", document.get("_id")));

				join.forEach(new Block<Document>() {
					public void apply(final Document document2) {
						String output= document2.getString("code") + "		" + document2.get("gender") + "		" + document2.get("age") + " 	" + document2.get("country") + " 	" + document2.get("singupDate") + "		" + document.get("total") ;

						writer.write(output);

					}
				});

			}
		});

	}

	@Override
	public void tracksChart(int n, boolean top) {
		int order = 1;
		if(top)
			order = -1;

		/*console query
		 * db.listened.aggregate( [ { $project: { "_id":0, "code": 1, "timeListened": { $size: { "$ifNull": [ "$timeListened", [] ] } } } } ] )
		 */

		Document groupByTracks = new Document("$project", new Document("trackId", 1).append("_id", 0).append("timeListened", new Document("$size", new Document( "$ifNull",Arrays.asList("$timeListened",Arrays.asList() ) ))));
		Document groupByUser = new Document("$group", new Document("_id", "$trackId").append("total", new Document("$sum", "$timeListened")));
		Document sort = new Document("$sort", new Document("total",order));
		Document limit = new Document("$limit", n);

		AggregateIterable<Document> iterable = db.getCollection(COLLECTIONLISTENED).aggregate(asList(groupByTracks,groupByUser,sort,limit)).allowDiskUse(true);
		iterable.forEach(new Block<Document>() {
			public void apply(final Document document) {

				/*
				 * db.artists.find({ "songs.trackId": "1c061863-1d3e-4066-aa93-5c9ce0bf72f2" }, { "songs.$": 1, _id: 0 } )
				 */
				Document query = new Document("songs.trackId", document.get("_id"));
				FindIterable<Document> join = db.getCollection(COLLECTIONARTISTS).find(query).projection(new Document("songs.$",1).append("_id", 0));

				join.forEach(new Block<Document>() {
					public void apply(final Document document2) {

						@SuppressWarnings("unchecked")
						Document documentOutput = ( ( (ArrayList<Document>)document2.get("songs") ).get(0));

						String output= documentOutput.getString("trackId").toString()  + "	" + documentOutput.get("trackName")  + "	" + document.get("total") ;

						writer.write(output);

					}
				});


			}
		});

	}

	@Override
	public void artistsChart(int n, boolean top) {
		int order = 1;
		if(top)
			order = -1;

		/*console query
		 * db.listened.aggregate( [ { $project: { "_id":0, "code": 1, "timeListened": { $size: { "$ifNull": [ "$timeListened", [] ] } } } } ] )
		 */

		Document groupByTracks = new Document("$project", new Document("trackId", 1).append("_id", 0).append("timeListened", new Document("$size", new Document( "$ifNull",Arrays.asList("$timeListened",Arrays.asList() ) ))));
		Document groupByUser = new Document("$group", new Document("_id", "$trackId").append("total", new Document("$sum", "$timeListened")));
		Document sort = new Document("$sort", new Document("total",order));
		Document limit = new Document("$limit", n);

		AggregateIterable<Document> iterable = db.getCollection(COLLECTIONLISTENED).aggregate(asList(groupByTracks,groupByUser,sort,limit)).allowDiskUse(true);
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

						String output = document2.get("artistId").toString() + "	"+ document2.get("artistName").toString() + "	" +  document.get("total").toString();

						writer.write(output);

					}
				});
			}
		});	

	}

	@Override
	public void artistByCode(String artistCode) {
		Document query = new Document("artistId", artistCode);
		FindIterable<Document> iterable = db.getCollection(COLLECTIONARTISTS).find(query);
		
		if(iterable.first() != null)
			this.writer.write(iterable.first().get("artistId").toString(),iterable.first().get("artistName").toString());

	}

	@Override
	public void artistByName(String artistName) {

		FindIterable<Document> iterable = db.getCollection(COLLECTIONARTISTS).find(new Document("artistName", artistName));

		if(iterable.first() != null)
			this.writer.write(iterable.first().get("artistId").toString(), iterable.first().get("artistName").toString());

	}

	@Override
	public void usersByAgeRange(int lowerBound, int upperBound) {

		// db.users.find( { age: { $gt: 10, $lt: 40 } } );
		FindIterable<Document> iterable = db.getCollection(COLLECTIONUSERS).find(
				new Document("age", new Document("$gt", lowerBound).append("$lt", upperBound)));

		iterable.forEach(new Block<Document>() {
			@Override
			public void apply(final Document document) {
				String output = document.get("code").toString() + " 	" + document.get("gender") + " 		" + document.get("age") + " 	" + document.get("country") + " 	" + document.get("singupDate");
				writer.write(output);
			}
		});

	}

	@Override
	public void oneTrackListenedByUser(String userCode) {
		
		//db.listened.aggregate( [ { $match: { code: 'user_000581' } },{ $project: { "_id":0, "trackId": 1 } }])
		
		Document match = new Document("$match", new Document("code", userCode));
		Document project = new Document("$project", new Document("_id", 0).append("trackId", 1));

		AggregateIterable<Document> iterable = db.getCollection(COLLECTIONLISTENED).aggregate(asList(match,project));
		
		String trackId = iterable.first().get("trackId").toString();
		
		//db.artists.find({"songs.trackId":"29bd015c59990a0ab269f31a6c2c5c8e56862359"}, {"songs.$":1})
		Document trackNameJoin = new Document("songs.trackId", trackId);
		Document projectJoin = new Document("_id", 0).append("songs.$", 1);
		FindIterable<Document> iterable2 = db.getCollection(COLLECTIONARTISTS).find(trackNameJoin).projection(projectJoin);

		iterable2.forEach(new Block<Document>() {
			public void apply(final Document document2) {
			
				@SuppressWarnings("unchecked")
				Document documentOutput = ( ( (ArrayList<Document>)document2.get("songs") ).get(0));				
				
				writer.write(documentOutput.get("trackId").toString(), documentOutput.get("trackName").toString());

			}
		});

	}

	@Override
	public void usersCountByCountry() {
		// db.users.aggregate( [{ $group: { _id: "$country", total: { $sum: 1 } } } ])

		Document groupByCountry = new Document("$group", new Document("_id", "$country").append("total", new Document("$sum",1)));

		AggregateIterable<Document> iterable = db.getCollection(COLLECTIONUSERS).aggregate(asList(groupByCountry));

		iterable.forEach(new Block<Document>() {
			public void apply(final Document document) {
				if(document.get("_id") != null)
					writer.write(document.get("_id").toString() ,document.get("total").toString());

			}
		});
	}

	@Override
	public void usersCountByCountryAndGender() {
		/*console query
		 * db.users.aggregate( [ { $group: { _id: {"country": "$country","gender": "$gender"}, total: { $sum: 1 } } } ])
		 */
		Document groupByTracks = new Document("$group", new Document("_id", new Document("country","$country").append("gender", "$gender")).append("total", new Document("$sum", 1)));

		AggregateIterable<Document> iterable = db.getCollection(COLLECTIONUSERS).aggregate(asList(groupByTracks));
		iterable.forEach(new Block<Document>() {
			public void apply(final Document document) {

				Document parcialOutput = (Document) document.get("_id");
				String country = (String) parcialOutput.get("country");
				String gender = (String) parcialOutput.get("gender");

				if(country != null && gender != null )
					writer.write(country,gender,document.get("total").toString());

			}
		});	



	}

}
