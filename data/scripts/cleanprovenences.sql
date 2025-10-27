SELECT monarchy_db2.provenence.*
FROM monarchy_db2.provenence
         LEFT JOIN monarch ON monarch.monarch_id =monarchy_db2.provenence.father
WHERE monarchy_db2.provenence.father IS NOT NULL
  AND monarch.monarch_id IS NULL;

SELECT *
FROM provenence
WHERE provenence_id IN (
    SELECT provenence_id
    FROM provenence
    GROUP BY provenence_id
    HAVING COUNT(*) > 1
);