package com.nuxeo.export;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.core.util.StringList;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.work.AbstractWork;
import org.nuxeo.ecm.platform.audit.api.AuditQueryBuilder;
import org.nuxeo.ecm.platform.audit.api.AuditReader;
import org.nuxeo.ecm.platform.audit.api.LogEntry;
import org.nuxeo.runtime.api.Framework;

@SuppressWarnings("serial")
public class ExportAuditXLSWork extends AbstractWork {

	private static final Log log = LogFactory.getLog(ExportAuditXLSWork.class);

	private static final DateFormat DATE_FORMAT = new SimpleDateFormat("dd/MM/yyyy");

	private String email;
	protected File outputFile;

	public ExportAuditXLSWork(String email) {
		this.email = email;
	}

	@Override
	public String getTitle() {
		return "CSV Report work";
	}

	@Override
	public void work() {
		log.info("Starting Audit XLS export");
		setProgress(Progress.PROGRESS_INDETERMINATE);

		openSystemSession();

		AuditReader reader = Framework.getService(AuditReader.class);

		XSSFWorkbook workbook = new XSSFWorkbook();
		XSSFSheet sheet = workbook.createSheet("Nuxeo Audit Export");

		try {
			outputFile = Framework.createTempFile("export", ".xls");
		} catch (IOException e) {
			// TODO
		}

		// Headers
		int rowNum = 0;
		int colNum = 0;
		Row row = sheet.createRow(rowNum++);
		for (String columnHeader : Arrays.asList("Performed Action", "Date", "Username", "Category", "Document",
				"Comment", "State")) {
			Cell cell = row.createCell(colNum++);
			cell.setCellValue((String) columnHeader);
		}

		long offset = 0;
		long limit = 1000;
		boolean query = true;

		while (query) {
			AuditQueryBuilder builder = new AuditQueryBuilder();
			builder.offset(offset);
			builder.limit(limit);
			// TODO FILTER
			// builder.predicates(Predicates.eq("docUUID", doc.getId()),
			// Predicates.eq("repositoryId", 'myRepository'));
			List<LogEntry> logEntries = reader.queryLogs(builder);
			log.info("Audit XLS export, querying " + offset + "-" + (offset + limit) + " > " + logEntries.size());

			for (LogEntry logEntry : logEntries) {
				colNum = 0;
				row = sheet.createRow(rowNum++);

				Cell cell = row.createCell(colNum++);
				cell.setCellValue(logEntry.getEventId());
				cell = row.createCell(colNum++);
				cell.setCellValue(DATE_FORMAT.format(logEntry.getEventDate()));
				cell = row.createCell(colNum++);
				cell.setCellValue(logEntry.getPrincipalName());
				cell = row.createCell(colNum++);
				cell.setCellValue(logEntry.getCategory());
				cell = row.createCell(colNum++);
				cell.setCellValue(logEntry.getDocUUID());
				cell = row.createCell(colNum++);
				cell.setCellValue(logEntry.getComment());
				cell = row.createCell(colNum++);
				cell.setCellValue(logEntry.getDocLifeCycle());
			}

			offset += limit;
			query = !logEntries.isEmpty();
		}

		try {
			FileOutputStream outputStream = new FileOutputStream(outputFile);
			workbook.write(outputStream);
			workbook.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		setStatus("Done");
		log.info("Audit XLS export done,  file is at " + outputFile);

		sendEmail();
		log.info("Audit XLS export sent to " + email);
	}

	protected void sendEmail() {
		String subject = "Export Audit XLS";
		String body = "Please find attached the requested audit export in XLS format.";
		String from = Framework.getProperty("mail.from");

		AutomationService automation = Framework.getService(AutomationService.class);

		try {
			Blob blob = Blobs.createBlob(outputFile, "application/xls");

			DocumentModel docToSend = session.createDocumentModel("File");
			docToSend.setPropertyValue("file:content", (Serializable) blob);
			docToSend.setPropertyValue("dublincore:title", "nuxeo-audit-export.xls");

			OperationContext ctx = new OperationContext(session);
			ctx.setInput(docToSend);

			ctx.put("from", from);
			ctx.put("to", email);
			ctx.put("subject", subject);
			ctx.put("message", body);
			String[] str = { "file:content" };
			ctx.put("files", new StringList(str));

			automation.run(ctx, "Document.Mail");
		} catch (Exception e) {
			throw new NuxeoException(e);
		}
	}

}
