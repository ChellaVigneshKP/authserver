-- Whole-second rounding can make a just-issued token appear to come from the future.
-- Preserve the UTC issuance precision supplied by CreateTokenWithHash.
ALTER TABLE Token.Token ALTER COLUMN CreatedOn DATETIME2(7) NOT NULL;
GO
