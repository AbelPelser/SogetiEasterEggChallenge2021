package org.easteregg.chaincode;


import lombok.extern.slf4j.Slf4j;
import org.hyperledger.fabric.shim.Chaincode;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.hyperledger.fabric.shim.ledger.KeyModification;
import org.hyperledger.fabric.shim.ledger.QueryResultsIterator;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import java.time.Instant;
import java.util.Collections;

import static org.easteregg.chaincode.global.TestConstants.TEST_CREATOR;
import static org.easteregg.chaincode.SkeletonCC.*;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static shared.GlobalConfig.CcFunction.QUERY_HISTORY;

@Slf4j
@RunWith(MockitoJUnitRunner.class)
public class SkeletonCCTest {

    @Spy
    private SkeletonCC skeletonCC;
    @Mock
    private ChaincodeStub chaincodeStub;

    // Utility functions
    private void setupMockInvocation() {
        Invocation invocation = new Invocation(chaincodeStub);
        doReturn(invocation).when(skeletonCC).makeInvocation(any(ChaincodeStub.class));
    }

    private void assertSuccessResponse(Response response, String msg) {
        if (response.getStatusCode() != 200) {
            String payload = response.getPayload() == null ? "" : response.getStringPayload();
            log.info("Invoke should have succeeded but did not, message: {}, {}", response.getMessage(), payload);
        }
        assertThat(response.getStatusCode()).isEqualTo(200);
        if (msg != null) {
            assertThat(response.getMessage()).isEqualTo(msg);
        }
    }

    private void assertSuccessResponse(Response response) {
        assertSuccessResponse(response, null);
    }

    private void assertFailureResponse(Response response, String msg) {
        if (response.getStatusCode() != 500) {
            log.info("Invoke should have failed but did not, message: {}", response.getMessage());
        }
        assertThat(response.getStatusCode()).isEqualTo(500);
        if (msg != null) {
            assertThat(response.getMessage()).isEqualTo(msg);
        }
    }

    private void assertFailureResponse(Response response) {
        assertFailureResponse(response, null);
    }

    private Response invokeAndAssertFailure(String msg) {
        setupMockInvocation();
        Chaincode.Response response = skeletonCC.invoke(chaincodeStub);
        assertFailureResponse(response, msg);
        return response;
    }

    private Response invokeAndAssertFailure() {
        return invokeAndAssertFailure(null);
    }

    private Response invokeAndAssertSuccess(String msg) {
        setupMockInvocation();
        Chaincode.Response response = skeletonCC.invoke(chaincodeStub);
        assertSuccessResponse(response, msg);
        return response;
    }

    private Response invokeAndAssertSuccess() {
        return invokeAndAssertSuccess(null);
    }

    @Before
    public void doBefore() {
        when(chaincodeStub.getCreator()).thenReturn(TEST_CREATOR);
    }

    // Init tests
    @Test
    public void initShouldReturnSuccess() {
        given(chaincodeStub.getParameters()).willReturn(Collections.emptyList());
        setupMockInvocation();
        Chaincode.Response result = skeletonCC.init(chaincodeStub);
        assertSuccessResponse(result);
    }

    @Test
    public void initShouldReturnError() {
        given(chaincodeStub.getParameters()).willReturn(Collections.singletonList("Apache_helicopter"));
        setupMockInvocation();
        assertFailureResponse(skeletonCC.init(chaincodeStub), Invocation.INCORRECT_N_ARGS_MSG);
    }

    // Invoke tests
    @Test
    public void invokeShouldReturnErrorForIncorrectFunctionName() {
        String functionName = "doMyLaundry";
        doReturn(functionName).when(chaincodeStub).getFunction();
        invokeAndAssertFailure(functionName + Invocation.UNSUPPORTED_FUNCTION_MSG);
    }

    // queryForHistory tests
    @Test
    public void shouldReturnJsonObjectForHistoryData() {
        String trainName = "redDevil";
        doReturn(QUERY_HISTORY.getName()).when(chaincodeStub).getFunction();
        doReturn(Collections.singletonList(trainName)).when(chaincodeStub).getParameters();
        Instant instant1 = Instant.ofEpochMilli(1543005164982L);
        Instant instant2 = Instant.ofEpochMilli(1549805164982L);

        KeyModification firstKeyModification = new MockKeyModification("tx1", "1", instant1, false);
        KeyModification secondKeyModification = new MockKeyModification("tx2", "3", instant2,
            false);
        QueryResultsIterator<KeyModification> queryResultsIterator = mockQueryResultIterator(firstKeyModification,
            secondKeyModification);
        doReturn(queryResultsIterator).when(chaincodeStub).getHistoryForKey(trainName);

        Response result = invokeAndAssertSuccess();

        String msg = result.getMessage();
        String res =
            "{\"transactions\":[[{\"transactionId\":\"tx1\",\"timestamp\":\"" + instant1.toString()
                + "\",\"value\":\"1\",\"isDeleted\":false},{\"transactionId\":\"tx2\",\"timestamp\":\""
                + instant2.toString() + "\",\"value\":\"3\",\"isDeleted\":false}]]}";
        JSONAssert.assertEquals(msg, res, JSONCompareMode.NON_EXTENSIBLE);
    }

    private QueryResultsIterator<KeyModification> mockQueryResultIterator(KeyModification firstKey,
        KeyModification secondKey) {
        return new MockQueryResultsIterator(firstKey, secondKey);
    }

}