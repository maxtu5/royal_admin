#!/bin/bash
MMLKT_DB_PASSWORD=root;MMLKT_DB_URL=jdbc:mysql://localhost:3306/monarchy_db2;MMLKT_DB_USER=root

# === CONFIGURATION ===
DB_USER="root"
DB_PASS="root"
DB_NAME="monarchy_db2"
BACKUP_DIR="C:\Users\MT\IdeaProjects\royal_admin\data\bu"
TIMESTAMP=$(date +"%Y-%m-%d_%H-%M-%S")
BACKUP_FILE="$BACKUP_DIR/${DB_NAME}_backup_$TIMESTAMP.sql"

# === EXECUTE BACKUP ===
echo "🔄 Starting backup for database: $DB_NAME"
mysqldump -u $DB_USER -p$DB_PASS $DB_NAME > "$BACKUP_FILE"

if [ $? -eq 0 ]; then
    echo "✅ Backup successful: $BACKUP_FILE"
else
    echo "❌ Backup failed"
fi