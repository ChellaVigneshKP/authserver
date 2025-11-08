IF NOT EXISTS(SELECT TABLE_NAME
              FROM INFORMATION_SCHEMA.COLUMNS
              WHERE TABLE_NAME = 'AuthSession'
                AND COLUMN_NAME = 'Pin'
                AND TABLE_SCHEMA = 'Token')
    BEGIN
        ALTER TABLE [Token].[AuthSession]
            ADD Pin [VARCHAR](512) NULL
    END
GO