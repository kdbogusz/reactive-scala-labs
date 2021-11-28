

sbt "runMain EShop.lab6.CatalogClusterNodeApp seed-node1" &
sbt "runMain EShop.lab6.CatalogClusterNodeApp seed-node2" &
sbt "runMain EShop.lab6.CatalogClusterNodeApp" &
sbt "runMain EShop.lab6.CatalogHttpClusterApp 9001" &
sbt "runMain EShop.lab6.CatalogHttpClusterApp 9002" &
sbt "runMain EShop.lab6.CatalogHttpClusterApp 9003" &


# start gatling tests
#sbt gatling-it:test
#sbt gatling-it:lastReport