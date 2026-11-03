/**
 * Root of the Calozone API.
 *
 * <p>Code is packaged by feature: {@code identity}, {@code program}, {@code training}, {@code
 * verdict}, {@code sleep} and {@code fuel}, plus {@code common} for cross-cutting pieces. Each
 * feature owns its controller, service, repository, entities and DTOs; other features reach it only
 * through its service.
 */
package app.calozone;
