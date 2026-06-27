package de.frank.invoice.worker.domain.invoice;

/**
 * Represents the supplier data extracted from or assigned to an invoice.
 *
 * @param name supplier name
 * @param street supplier street address
 * @param postalCode supplier postal code
 * @param city supplier city
 * @param country supplier country
 * @param taxId national tax identifier, if available
 * @param vatId VAT identifier, if available
 * @param iban supplier bank account IBAN, if available
 */
public record Supplier(
        String name,
        String street,
        String postalCode,
        String city,
        String country,
        String taxId,
        String vatId,
        String iban) {

}


