import org.neo4j.driver.v1.AuthTokens
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource

import scala.collection.JavaConversions._


object Importer extends App {

  // Wait until all the other stuff is started (only needed for this docker-compose example)
  Thread.sleep(15000)

  val mysqlDriver = new DriverManagerDataSource(sys.env("SQL_URL"))
  mysqlDriver.setDriverClassName("com.mysql.cj.jdbc.Driver")
  val mysqlTemplate = new JdbcTemplate(mysqlDriver)

  val neo4jDriver = new DriverManagerDataSource(sys.env("NEO4J_URL"), sys.env("NEO4J_USER"), sys.env("NEO4J_PASSWORD"))
  neo4jDriver.setDriverClassName("org.neo4j.jdbc.bolt.BoltDriver")
  val neo4jTemplate = new JdbcTemplate(neo4jDriver)

  val beers = mysqlTemplate.queryForList("SELECT id, brewery_id, cat_id, style_id, name FROM `beers`").toList
  neo4jTemplate.execute("CREATE " +
      beers.map(beer => {
        s"(: Beer {id: ${beer.get("id")}, " +
            s"brewery: ${beer.get("brewery_id")}, " +
            (if (beer.get("cat_id").toString != "-1") s"category: ${beer.get("cat_id")}, " else "") +
            (if (beer.get("style_id").toString != "-1") s"style: ${beer.get("style_id")}, " else "") +
            s"name: '${cleanString(beer.get("name"))}'})"
      }).mkString(",")
  )

  val breweries = mysqlTemplate.queryForList("select b.id, b.name, b.city, b.state, b.country, g.latitude, g.longitude from `breweries` b LEFT JOIN `breweries_geocode` g ON b.id = g.brewery_id").toList
  neo4jTemplate.execute("CREATE " +
      breweries.map(brewery => {
        s"(: Brewery {id: ${brewery.get("id")}, " +
            (if (brewery.get("state").toString != "") s"city: '${cleanString(brewery.get("city"))}', " else "") +
            (if (brewery.get("state").toString != "") s"state: '${cleanString(brewery.get("state"))}', " else "") +
            (if (brewery.get("state").toString != "") s"country: '${cleanString(brewery.get("country"))}', " else "") +
            s"latitude: ${brewery.get("latitude")}, " +
            s"longitude: ${brewery.get("longitude")}, " +
            s"name: '${cleanString(brewery.get("name"))}'})"
      }).mkString(",")
  )

  val categories = mysqlTemplate.queryForList("select id, cat_name from `categories`").toList
  neo4jTemplate.execute("CREATE " +
      categories.map(category => {
        s"(: Category {id: ${category.get("id")}, " +
            s"name: '${cleanString(category.get("cat_name"))}'})"
      }).mkString(",")
  )

  val styles = mysqlTemplate.queryForList("select id, cat_id, style_name from `styles`").toList
  neo4jTemplate.execute("CREATE " +
      styles.map(style => {
        s"(: Style {id: ${style.get("id")}, " +
            s"category: ${style.get("cat_id")}, " +
            s"name: '${cleanString(style.get("style_name"))}'})"
      }).mkString(",")
  )

  neo4jTemplate.execute("CREATE" +
      "INDEX ON :Beer(id)," +
      "INDEX ON :Beer(name)," +
      "INDEX ON :Brewery(id)," +
      "INDEX ON :Brewery(name)," +
      "INDEX ON :Category(id)," +
      "INDEX ON :Style(id)"
  )

  neo4jTemplate.execute("schema await")

  // create relations
  neo4jTemplate.execute("MATCH (beer: Beer), (brewery: Brewery) WHERE beer.brewery = brewery.id MERGE (brewery)-[:PRODUCES]->(beer);")
  neo4jTemplate.execute("MATCH (beer: Beer), (category: Category) WHERE beer.category = category.id MERGE (beer)-[:HAS_CATEGORY]->(category);")
  neo4jTemplate.execute("MATCH (beer: Beer), (style: Style) WHERE beer.style = style.id MERGE (beer)-[:HAS_STYLE]->(style);")
  neo4jTemplate.execute("MATCH (category: Category), (style: Style) WHERE style.category = category.id MERGE (style)-[:REFINES]->(category);")


  private def cleanString(s: AnyRef) = s.toString.replace("'", "`")
}
