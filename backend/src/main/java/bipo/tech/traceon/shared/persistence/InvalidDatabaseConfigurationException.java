package bipo.tech.traceon.shared.persistence;

/** A configuração do banco impede a partida; a mensagem nunca traz o valor configurado. */
class InvalidDatabaseConfigurationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    InvalidDatabaseConfigurationException(String message) {
        super(message);
    }
}
