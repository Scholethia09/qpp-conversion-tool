package gov.cms.qpp.conversion.api.services;


import gov.cms.qpp.conversion.Converter;
import gov.cms.qpp.conversion.api.exceptions.UncheckedInterruptedException;
import gov.cms.qpp.conversion.api.helper.MetadataHelper;
import gov.cms.qpp.conversion.api.model.Metadata;
import gov.cms.qpp.conversion.encode.JsonWrapper;
import gov.cms.qpp.conversion.model.Node;
import net.jodah.concurrentunit.Waiter;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mockStatic;


@RunWith(PowerMockRunner.class)
@PrepareForTest({MetadataHelper.class})
public class AuditServiceImplTest {
	private static final String AN_ID = "1234567890";
	private static final String FILENAME = "file";

	@InjectMocks
	private AuditServiceImpl underTest;

	@Mock
	private StorageService storageService;

	@Mock
	private DbService dbService;

	@Mock
	private Converter.ConversionReport report;

	private Metadata metadata;
	private InputStream fileContent = new ByteArrayInputStream("Hello".getBytes());

	@Before
	public void before() throws NoSuchFieldException, IllegalAccessException {
		metadata = new Metadata();

		mockStatic(MetadataHelper.class);
		when(MetadataHelper.generateMetadata(any(Node.class), any(MetadataHelper.Outcome.class)))
				.thenReturn(metadata);
		doReturn(CompletableFuture.completedFuture(metadata))
				.when(dbService).write(metadata);
	}

	@Test
	public void testAuditHappyPath() {
		successfulEncodingPrep();
		allGood();
		underTest.success(report);

		assertThat(metadata.getQppLocator()).isSameAs(AN_ID);
		assertThat(metadata.getSubmissionLocator()).isSameAs(AN_ID);
		assertThat(metadata.getFileName()).isSameAs(FILENAME);
	}

	@Test
	public void testAuditHappyPathWrite() {
		successfulEncodingPrep();
		allGood();
		underTest.success(report);

		verify(dbService, times(1)).write(metadata);
	}

	@Test
	public void testFileUploadFailureException() throws TimeoutException {
		successfulEncodingPrep();
		problematic();
		final Waiter waiter = new Waiter();
		CompletableFuture<Void> future = underTest.success(report);

		future.whenComplete((nada, ex) -> {
			waiter.assertNull(metadata.getQppLocator());
			waiter.assertNull(metadata.getSubmissionLocator());
			waiter.assertTrue(ex.getCause() instanceof UncheckedInterruptedException);
			waiter.resume();
		});

		waiter.await(5000);
	}

	@Test
	public void testAuditConversionFailureHappy() {
		errorPrep();
		allGood();
		underTest.failConversion(report);

		assertThat(metadata.getConversionErrorLocator()).isSameAs(AN_ID);
		assertThat(metadata.getSubmissionLocator()).isSameAs(AN_ID);
	}

	@Test
	public void testAuditQppValidationFailureHappy() {
		successfulEncodingPrep();
		errorPrep();
		allGood();
		underTest.failValidation(report);

		assertThat(metadata.getValidationErrorLocator()).isSameAs(AN_ID);
		assertThat(metadata.getQppLocator()).isSameAs(AN_ID);
		assertThat(metadata.getSubmissionLocator()).isSameAs(AN_ID);
	}

	private void successfulEncodingPrep() {
		prepOverlap();
		JsonWrapper wrapper = new JsonWrapper();
		wrapper.putString("meep", "mawp");

		when(report.getEncoded()).thenReturn(wrapper);
	}

	private void errorPrep() {
		prepOverlap();

		when(report.streamDetails()).thenReturn(fileContent);
	}

	private void prepOverlap() {
		Node node = new Node();
		node.putValue("meep", "mawp");

		when(report.getFileInput()).thenReturn(fileContent);
		when(report.getDecoded()).thenReturn(node);
		when(report.getFilename()).thenReturn(FILENAME);
	}

	private void allGood() {
		when(storageService.store(any(String.class), any(InputStream.class)))
				.thenReturn(CompletableFuture.completedFuture(AN_ID));
	}

	private void problematic() {
		when(storageService.store(any(String.class), any(InputStream.class)))
				.thenReturn(CompletableFuture.supplyAsync( () -> {
					throw new UncheckedInterruptedException(new InterruptedException());
				}));
	}

}


