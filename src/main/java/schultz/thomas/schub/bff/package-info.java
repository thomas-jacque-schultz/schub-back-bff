/**
 * Le BFF : la porte d'entrée du front. Sans état — il authentifie, puis route.
 *
 * <h2>Les trois couches, et le sens des flèches</h2>
 *
 * <pre>
 *   api  ---&gt;  business  ---&gt;  data
 * </pre>
 *
 * <ul>
 *   <li>{@code api} — contrôleurs et formes transportées sur le fil ({@code dto}). C'est la
 *       seule surface publiable : un consommateur qui parle à ce service n'a besoin de
 *       connaître que ce paquet.</li>
 *   <li>{@code business} — les services, la logique. Il connaît {@code data} et les DTO,
 *       <strong>jamais les contrôleurs</strong>.</li>
 *   <li>{@code data} — ce qui est persisté et les formes brutes des systèmes externes. Il ne
 *       connaît personne.</li>
 *   <li>{@code config} — transverse, à la racine : il câble les trois couches, donc il les voit
 *       toutes. C'est le seul paquet autorisé à le faire.</li>
 * </ul>
 *
 * <p>Ce découpage n'est pas décoratif : il est ce qui empêche les imports de partir dans tous
 * les sens, et ce qui rend le contrat d'un service isolable de son implémentation.</p>
 */
package schultz.thomas.schub.bff;
