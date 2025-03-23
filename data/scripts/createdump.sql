mysqldump -u root -p --databases monarchy_db2 --ignore-table=monarchy_db2.wiki_cache_record --result-file=./data/exchange/dump.sql
mysql -u root -p < ./src/dump.sql