package org.yottabase.lastfm.driver.mongodb;

import org.bson.Document;
import org.yottabase.lastfm.core.Driver;
import org.yottabase.lastfm.importer.ListenedTrack;
import org.yottabase.lastfm.importer.User;

import com.mongodb.BasicDBObject;
import com.mongodb.Block;
import com.mongodb.MongoClient;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;

public class MongodbDriver implements Driver {
	private MongoClient mongoClient;
	private MongoDatabase db;

	private final static String DATABASE = "lastfm";
	private final static String COLLECTIONUSERS = "users";
	private final static String COLLECTIONTRACKS = "tracks";
	private final static String COLLECTIONARTISTS = "artists";

	private boolean artistDuplicate;
	private boolean trackDuplicate;

	public MongodbDriver(MongoClient client) {
		this.mongoClient = client;
	}

	@Override
	public void initializeSchema() {
		db = mongoClient.getDatabase(DATABASE);

		db.drop();

		System.out.println("crea schema");

		/*
		 * index options
		 */

		IndexOptions indOpt = new IndexOptions();
		indOpt.unique(true);

		// create tracks index

		db.getCollection(COLLECTIONTRACKS).createIndex(
				new Document("trackId", 1), indOpt);

		// create artist index

		db.getCollection(COLLECTIONARTISTS).createIndex(
				(new Document("artistId", 1)), indOpt);

	}

	@Override
	public void insertUser(User user) {

		db.getCollection(COLLECTIONUSERS).insertOne(
				new Document("code", user.getCode())
						.append("gender", user.getGender())
						.append("age", user.getAge())
						.append("country", user.getCountry())
						.append("singupDate", user.getSignupDateAsJavaDate()));
	}

	@Override
	public void insertListenedTrack(ListenedTrack listenedTrack) {
		artistDuplicate = false;
		trackDuplicate = false;
		FindIterable<Document> iterable;

		/*
		 * insert tracks
		 */
		iterable = db.getCollection(COLLECTIONTRACKS).find(
				new Document("trackId", listenedTrack.getTrackCode()));

		iterable.forEach(new Block<Document>() {
			@Override
			public void apply(final Document document) {
				trackDuplicate = true;
			}
		});

		if (!trackDuplicate) {
			db.getCollection(COLLECTIONTRACKS).insertOne(
					new Document("trackId", listenedTrack.getTrackCode())
							.append("trackName", listenedTrack.getTrackName()));

		}

		/*
		 * insert artists
		 */
		iterable = db.getCollection(COLLECTIONARTISTS).find(
				new Document("artistId", listenedTrack.getArtistCode()));

		iterable.forEach(new Block<Document>() {
			@Override
			public void apply(final Document document) {
				artistDuplicate = true;
			}
		});

		if (!artistDuplicate) {
			db.getCollection(COLLECTIONARTISTS)
					.insertOne(
							new Document("artistId", listenedTrack
									.getArtistCode()).append("artistName",
									listenedTrack.getArtistName()));
		}

		/*
		 * associo il brano all'utente che lo ha ascoltato
		 */
		BasicDBObject trackObject = new BasicDBObject();
		trackObject.put("trackId", listenedTrack.getTrackCode());
		trackObject.put("time", listenedTrack.getTimeAsJavaDate());

		db.getCollection(COLLECTIONUSERS).updateOne(
				new BasicDBObject("code", listenedTrack.getCode()),
				new BasicDBObject("$push", new BasicDBObject("tracks",
						trackObject)));

		/*
		 * associo il brano all'artista
		 */
		BasicDBObject ArtistTracks = new BasicDBObject();
		ArtistTracks.put("trackId", listenedTrack.getTrackCode());

		db.getCollection(COLLECTIONUSERS).updateOne(
				new BasicDBObject("code", listenedTrack.getCode()),
				new BasicDBObject("$push", new BasicDBObject("tracks",
						ArtistTracks)));

	}

}
