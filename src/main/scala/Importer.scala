import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.SimpleDriverDataSource

import scala.collection.JavaConversions._


object Importer extends App {

  // Wait until all the other stuff is started (only needed for this docker-compose example)
  Thread.sleep(15000)

  val mysqlDriver = new SimpleDriverDataSource(new com.mysql.cj.jdbc.Driver(), sys.env("SQL_URL"))
  val mysqlTemplate = new JdbcTemplate(mysqlDriver)

  val neo4jDriver = new SimpleDriverDataSource(new org.neo4j.jdbc.bolt.BoltDriver(), sys.env("NEO4J_URL"))
  val neo4jTemplate = new JdbcTemplate(neo4jDriver)

  println(">>> Starting migration for beers")
  val beers = mysqlTemplate.queryForList("SELECT id, brewery_id, cat_id, style_id, name FROM `beers`").toList
  beers.foreach(beer => {
    neo4jTemplate.execute("CREATE " +
        s"(: Beer {id: ${beer.get("id")}, " +
        s"brewery: ${beer.get("brewery_id")}, " +
        (if (beer.get("cat_id").toString != "-1") s"category: ${beer.get("cat_id")}, " else "") +
        (if (beer.get("style_id").toString != "-1") s"style: ${beer.get("style_id")}, " else "") +
        s"name: '${cleanString(beer.get("name"))}'})")
  })

  println(">>> Starting migration for breweries")
  val breweries = mysqlTemplate.queryForList("select b.id, b.name, b.city, b.state, b.country, g.latitude, g.longitude from `breweries` b LEFT JOIN `breweries_geocode` g ON b.id = g.brewery_id").toList
  breweries.foreach(brewery => {
    neo4jTemplate.execute("CREATE " +
        s"(: Brewery {id: ${brewery.get("id")}, " +
        (if (brewery.get("state").toString != "") s"city: '${cleanString(brewery.get("city"))}', " else "") +
        (if (brewery.get("state").toString != "") s"state: '${cleanString(brewery.get("state"))}', " else "") +
        (if (brewery.get("state").toString != "") s"country: '${cleanString(brewery.get("country"))}', " else "") +
        s"latitude: ${brewery.get("latitude")}, " +
        s"longitude: ${brewery.get("longitude")}, " +
        s"name: '${cleanString(brewery.get("name"))}'})")
  })

  println(">>> Starting migration for categories")
  val categories = mysqlTemplate.queryForList("select id, cat_name from `categories`").toList
  neo4jTemplate.execute("CREATE " +
      categories.map(category => {
        s"(: Category {id: ${category.get("id")}, " +
            s"name: '${cleanString(category.get("cat_name"))}'})"
      }).mkString(",")
  )

  println(">>> Starting migration for beers")
  val styles = mysqlTemplate.queryForList("select id, cat_id, style_name from `styles`").toList
  neo4jTemplate.execute("CREATE " +
      styles.map(style => {
        s"(: Style {id: ${style.get("id")}, " +
            s"category: ${style.get("cat_id")}, " +
            s"name: '${cleanString(style.get("style_name"))}'})"
      }).mkString(",")
  )

  println(">>> Starting migration for indexes")
  neo4jTemplate.execute("CREATE" +
      "INDEX ON :Beer(id)," +
      "INDEX ON :Beer(name)," +
      "INDEX ON :Brewery(id)," +
      "INDEX ON :Brewery(name)," +
      "INDEX ON :Category(id)," +
      "INDEX ON :Style(id)"
  )

  println(">>> Starting migration for schema")
  neo4jTemplate.execute("schema await")

  println(">>> Starting migration for relations")
  // create relations
  neo4jTemplate.execute("MATCH (beer: Beer), (brewery: Brewery) WHERE beer.brewery = brewery.id MERGE (brewery)-[:PRODUCES]->(beer);")
  neo4jTemplate.execute("MATCH (beer: Beer), (category: Category) WHERE beer.category = category.id MERGE (beer)-[:HAS_CATEGORY]->(category);")
  neo4jTemplate.execute("MATCH (beer: Beer), (style: Style) WHERE beer.style = style.id MERGE (beer)-[:HAS_STYLE]->(style);")
  neo4jTemplate.execute("MATCH (category: Category), (style: Style) WHERE style.category = category.id MERGE (style)-[:REFINES]->(category);")

  println(">>> Migration done \uD83C\uDF89")

  private def cleanString(s: AnyRef) = s.toString.replace("'", "`")
}
