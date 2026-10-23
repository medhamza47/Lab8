-- Migration 2.0: ajoute une description aux salles et un index unique sur l'e-mail.
ALTER TABLE salles ADD COLUMN description VARCHAR(255);
CREATE UNIQUE INDEX idx_utilisateur_email ON utilisateurs(email);
